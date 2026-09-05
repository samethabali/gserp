package com.gscrm.service;

import com.gscrm.model.Appointment;
import com.gscrm.model.WorkingHours;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.WorkingHoursRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchedulerService {

    private final WorkingHoursRepository workingHoursRepository;
    private final AppointmentRepository appointmentRepository;

    /**
     * Validate that the given time range falls within the staff's working hours.
     *
     * <p><b>Bilinçli olarak toleranslı:</b> bu aşırı yükleme mesai taşmasına izin verir
     * (bkz. 4 argümanlı sürüm). Panel takviminde uzmanlar günün son slotunu düzenli
     * olarak kullanıyor; taşmayı burada yasaklamak mevcut davranışta regresyon olurdu.
     * Online randevu yolu bu metodu <b>kullanmaz</b> — o taraf {@code AvailabilityService}
     * üzerinden gider ve mesai bitişini üreteç düzeyinde zaten uygular.
     */
    public boolean isWithinWorkingHours(Long staffId, LocalDateTime start, LocalDateTime end) {
        return isWithinWorkingHours(staffId, start, end, true);
    }

    /**
     * @param allowOverrun {@code false} ise randevunun bitişi de mesai penceresi içinde olmalı.
     */
    public boolean isWithinWorkingHours(Long staffId, LocalDateTime start, LocalDateTime end,
                                        boolean allowOverrun) {
        DayOfWeek dow = start.getDayOfWeek();
        List<WorkingHours> hours = workingHoursRepository.findByStaffId(staffId);

        // Kayıt hiç tanımlanmamışsa kısıtlama uygulanmaz.
        // StaffService.create çalışma saati seed etmediği için yeni uzmanda normal durum budur;
        // burayı "hiçbir şey uygun değil"e çevirmek panel takvimini kilitler.
        if (hours.isEmpty()) return true;

        boolean sameDay = end == null || start.toLocalDate().equals(end.toLocalDate());

        return hours.stream()
                .filter(wh -> wh.getDayOfWeek() == dow && !wh.isDayOff())
                .filter(wh -> wh.getStartTime() != null && wh.getEndTime() != null)
                .anyMatch(wh -> {
                    LocalTime s = start.toLocalTime();
                    boolean startOk = !s.isBefore(wh.getStartTime()) && s.isBefore(wh.getEndTime());
                    if (!startOk) return false;
                    if (allowOverrun || end == null) return true;
                    // Ertesi güne sarkan randevu hiçbir pencereye sığmaz.
                    return sameDay && !end.toLocalTime().isAfter(wh.getEndTime());
                });
    }

    /**
     * Check if a staff member has no conflicting appointments in the given time range.
     * Excludes the given appointmentId (for move operations).
     */
    public boolean isStaffAvailable(Long staffId, LocalDateTime start, LocalDateTime end, Long excludeAppointmentId) {
        List<Appointment> conflicts = busyBlocks(staffId, start, end);
        if (excludeAppointmentId != null) {
            conflicts = conflicts.stream()
                    .filter(a -> !a.getId().equals(excludeAppointmentId))
                    .toList();
        }
        return conflicts.isEmpty();
    }

    /**
     * Aralığa değen, iptal edilmemiş randevular — tek sorguda.
     *
     * <p>Bir günün tamamı için çağrılıp sonuç {@link #isFree} ile taranabilsin diye
     * ayrıldı. {@code AvailabilityService} her slot için ayrı bir çakışma sorgusu
     * atıyordu: 09:00-19:00 penceresi ve 15 dakikalık adımla bu, tek bir "müsait
     * saatler" isteğinde 39 SQL demekti. Ölçüm bunu doğruladı — uç toplam 79 sorgu
     * çıkarıyor ve on bağlantılık havuzu yirmi ziyaretçide doyuruyordu.
     */
    public List<Appointment> busyBlocks(Long staffId, LocalDateTime from, LocalDateTime to) {
        return appointmentRepository.findStaffOverlap(
                TenantContext.requireSalonId(), staffId, from, to, AppointmentStatus.CANCELLED);
    }

    /**
     * {@link #isStaffAvailable} ile <b>aynı</b> çakışma kuralı, önden getirilmiş
     * liste üzerinde. İki ayrı yerde iki ayrı kural yazılmasın diye tek satır:
     * randevu aralığa değiyorsa slot doludur.
     */
    public static boolean isFree(List<Appointment> busy, LocalDateTime start, LocalDateTime end) {
        return busy.stream().noneMatch(a ->
                a.getStartTime().isBefore(end) && a.getEndTime().isAfter(start));
    }
}
