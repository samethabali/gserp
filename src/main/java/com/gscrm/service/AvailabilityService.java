package com.gscrm.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gscrm.model.Appointment;
import com.gscrm.model.Staff;
import com.gscrm.model.WorkingHours;
import com.gscrm.repository.StaffRepository;
import com.gscrm.repository.WorkingHoursRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Online randevu için müsait saat üretimi.
 *
 * <p>Slot üretimi eskiden {@code BookingController} içinde elle 08:00-20:00 / 30 dk
 * ızgarasıyla yapılıyordu; tatil, geçmiş saat ve şube süre override'ı dikkate
 * alınmadığı için arayüzün gösterdiği saat ile POST tarafının kabul ettiği saat
 * ayrışabiliyordu. Burada tek bir üreteç var ve {@link #isBookable} da aynı
 * üreteci çağırıyor — gösterim ile kabulün uyuşması böyle garanti altına alınıyor.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);

    /** Uzmanın hiç çalışma saati tanımı yoksa kullanılan varsayılan pencere. */
    private static final LocalTime DEFAULT_OPEN = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_CLOSE = LocalTime.of(19, 0);
    private static final int DEFAULT_SLOT_STEP_MINUTES = 15;
    private static final int DEFAULT_MIN_LEAD_MINUTES = 60;

    private final WorkingHoursRepository workingHoursRepository;
    private final StaffRepository staffRepository;
    private final SchedulerService schedulerService;
    private final BranchHolidayService branchHolidayService;
    private final BranchPricingService branchPricingService;
    private final SalonSettingsService salonSettingsService;

    /**
     * Slot saati {@code "09:00"} olarak serileşmeli: booking.js saati
     * {@code ${date}T${time}:00} biçiminde birleştiriyor. Çıplak LocalTime
     * {@code "09:00:00"} üretir ve istek geçersiz bir tarih-saatle gelir.
     */
    public record TimeSlot(
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm") LocalTime time,
            boolean available) {
    }

    /**
     * Verilen gün için uzmanın slotlarını üretir. Boş liste "bu gün randevuya kapalı"
     * demektir (tatil, izin günü, geçmiş tarih veya pasif uzman).
     */
    public List<TimeSlot> slotsFor(Long staffId, Long serviceId, LocalDate date) {
        Long salonId = TenantContext.requireSalonId();
        if (staffId == null || serviceId == null || date == null) return List.of();

        // Geçmiş gün
        if (date.isBefore(LocalDate.now())) return List.of();

        // Şube tatili
        if (branchHolidayService.isHoliday(salonId, date)) return List.of();

        // Uzman var mı, aktif mi
        Staff staff = staffRepository.findByIdAndSalonId(staffId, salonId).orElse(null);
        if (staff == null || !staff.isActive()) return List.of();

        int duration = branchPricingService.effectiveDuration(serviceId);
        if (duration <= 0) return List.of();

        List<Window> windows = windowsFor(staffId, salonId, date.getDayOfWeek());
        if (windows.isEmpty()) return List.of();

        int step = intSetting("booking.slot_step_minutes", DEFAULT_SLOT_STEP_MINUTES, 5, 120);
        LocalDateTime earliest = LocalDateTime.now()
                .plusMinutes(intSetting("booking.min_lead_minutes", DEFAULT_MIN_LEAD_MINUTES, 0, 20160));

        // Günün dolu blokları tek sorguda alınır. Eskiden her slot için ayrı bir
        // çakışma sorgusu atılıyordu; ölçümde bu tek ucun 79 SQL'e mal olduğu ve
        // yirmi eşzamanlı ziyaretçide on bağlantılık havuzu doyurduğu görüldü.
        // Sorgu bir aralık taraması olduğu için önceki günden sarkan randevular da
        // kapsanır; slotlar mesai penceresini aşamadığı için gün sınırı yeterli.
        List<Appointment> busy = schedulerService.busyBlocks(
                staffId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());

        // Pencereler çakışabilir; aynı saat iki kez üretilmesin diye saate göre topla.
        Map<LocalTime, Boolean> byTime = new LinkedHashMap<>();

        for (Window window : windows) {
            LocalTime cursor = window.start();
            // Döngü koşulu bitişe göre: mesai bitişini aşan slot hiç üretilmez.
            while (!cursor.plusMinutes(duration).isAfter(window.end())) {
                LocalDateTime start = date.atTime(cursor);
                if (!start.isBefore(earliest)) {
                    boolean available = SchedulerService.isFree(
                            busy, start, start.plusMinutes(duration));
                    byTime.merge(cursor, available, (a, b) -> a || b);
                }
                LocalTime next = cursor.plusMinutes(step);
                if (!next.isAfter(cursor)) break; // gün sonu sarması
                cursor = next;
            }
        }

        return byTime.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TimeSlot(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Bu başlangıç saati gerçekten randevuya açık mı? {@link #slotsFor} ile aynı
     * üreteci kullanır — paralel bir implementasyon değildir.
     */
    public boolean isBookable(Long staffId, Long serviceId, LocalDateTime start) {
        if (start == null) return false;
        LocalTime wanted = start.toLocalTime();
        return slotsFor(staffId, serviceId, start.toLocalDate()).stream()
                .anyMatch(slot -> slot.time().equals(wanted) && slot.available());
    }

    /** O günün geçerli çalışma pencereleri; tanım yoksa salon varsayılanı. */
    private List<Window> windowsFor(Long staffId, Long salonId, DayOfWeek dayOfWeek) {
        List<WorkingHours> defined = workingHoursRepository.findByStaffIdAndSalonId(staffId, salonId);

        if (!defined.isEmpty()) {
            List<Window> windows = new ArrayList<>();
            boolean dayDefined = false;
            for (WorkingHours wh : defined) {
                if (wh.getDayOfWeek() != dayOfWeek) continue;
                dayDefined = true;
                if (wh.isDayOff() || wh.getStartTime() == null || wh.getEndTime() == null) continue;
                if (!wh.getStartTime().isBefore(wh.getEndTime())) continue;
                windows.add(new Window(wh.getStartTime(), wh.getEndTime()));
            }
            // Gün tanımlıysa (izin günü dahil) sonucu olduğu gibi döndür; varsayılana düşme.
            if (dayDefined) return windows;
        }

        // Uzmanın hiç tanımı yok (StaffService.create çalışma saati seed etmiyor) ya da
        // bu gün hiç tanımlanmamış: salon varsayılan penceresi.
        LocalTime open = timeSetting("booking.default_open", DEFAULT_OPEN);
        LocalTime close = timeSetting("booking.default_close", DEFAULT_CLOSE);
        if (!open.isBefore(close)) return List.of();
        return List.of(new Window(open, close));
    }

    private int intSetting(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(salonSettingsService.get(key, String.valueOf(fallback)).trim());
            if (value < min || value > max) return fallback;
            return value;
        } catch (NumberFormatException e) {
            log.warn("Geçersiz salon ayarı {} — varsayılan {} kullanılıyor", key, fallback);
            return fallback;
        }
    }

    private LocalTime timeSetting(String key, LocalTime fallback) {
        try {
            return LocalTime.parse(salonSettingsService.get(key, fallback.toString()).trim());
        } catch (DateTimeParseException e) {
            log.warn("Geçersiz salon ayarı {} — varsayılan {} kullanılıyor", key, fallback);
            return fallback;
        }
    }

    private record Window(LocalTime start, LocalTime end) {
    }
}
