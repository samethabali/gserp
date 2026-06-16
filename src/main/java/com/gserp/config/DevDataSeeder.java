package com.gserp.config;

import com.gserp.model.*;
import com.gserp.model.enums.*;
import com.gserp.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev profili için demo veri yükleyici. Veritabanı boşsa çalışır; aksi hâlde no-op.
 * Production profillerinde aktif değildir.
 */
@Slf4j
@Component
@Profile("dev")
@Order(10) // InitialAdminSeeder (default order) önce çalışsın
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private static final Long DEFAULT_SALON_ID = 1L;

    private final StaffRepository staffRepository;
    private final ResourceRepository resourceRepository;
    private final ServiceDefinitionRepository serviceRepository;
    private final CustomerRepository customerRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final AppointmentRepository appointmentRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (staffRepository.count() > 0) {
            log.info("Dev seed skipped — DB already populated");
            return;
        }
        log.info("Seeding dev data...");

        List<Staff> staff = seedStaff();
        List<Resource> resources = seedResources();
        List<ServiceDefinition> services = seedServices(resources);
        seedCustomers();
        seedWorkingHours(staff);
        seedAppointments(staff, services);

        log.info("Dev seed complete: {} staff, {} resources, {} services, {} appointments",
                staffRepository.count(), resourceRepository.count(),
                serviceRepository.count(), appointmentRepository.count());
    }

    private List<Staff> seedStaff() {
        LocalDateTime now = LocalDateTime.now();
        List<Staff> all = List.of(
                build("Ayşe Yılmaz", "0532 100 0001", "ayse@salon.com",  StaffRole.SPECIALIST, "#9b59b6"),
                build("Fatma Demir", "0532 100 0002", "fatma@salon.com", StaffRole.SPECIALIST, "#e91e8c"),
                build("Elif Kaya",   "0532 100 0003", "elif@salon.com",  StaffRole.SPECIALIST, "#f39c12"),
                build("Zeynep Çelik","0532 100 0004", "zeynep@salon.com",StaffRole.SPECIALIST, "#2ecc71"),
                build("Banko",       "0532 100 0000", "banko@salon.com", StaffRole.RECEPTIONIST, "#3498db")
        );
        all.forEach(s -> { s.setCreatedAt(now); s.setUpdatedAt(now); });
        return staffRepository.saveAll(all);
    }

    private Staff build(String name, String phone, String email, StaffRole role, String color) {
        return Staff.builder().salonId(DEFAULT_SALON_ID).name(name).phone(phone).email(email)
                .role(role).colorHex(color).active(true).build();
    }

    private List<Resource> seedResources() {
        LocalDateTime now = LocalDateTime.now();
        List<Resource> all = List.of(
                res("Cilt Bakım Odası 1", ResourceType.ROOM),
                res("Cilt Bakım Odası 2", ResourceType.ROOM),
                res("Lazer Odası",         ResourceType.ROOM),
                res("Lazer Cihazı",        ResourceType.DEVICE),
                res("Tırnak Masası 1",     ResourceType.EQUIPMENT),
                res("Tırnak Masası 2",     ResourceType.EQUIPMENT)
        );
        all.forEach(r -> r.setCreatedAt(now));
        return resourceRepository.saveAll(all);
    }

    private Resource res(String name, ResourceType type) {
        return Resource.builder().salonId(DEFAULT_SALON_ID).name(name).resourceType(type).capacity(1).active(true).build();
    }

    private List<ServiceDefinition> seedServices(List<Resource> resources) {
        // resources: 0=Cilt1, 1=Cilt2, 2=LazerOda, 3=LazerCihaz, 4=Tırnak1, 5=Tırnak2
        Long cilt1 = resources.get(0).getId();
        Long cilt2 = resources.get(1).getId();
        Long lazerO = resources.get(2).getId();
        Long lazerC = resources.get(3).getId();
        Long tirnak1 = resources.get(4).getId();
        Long tirnak2 = resources.get(5).getId();

        LocalDateTime now = LocalDateTime.now();
        List<ServiceDefinition> all = new ArrayList<>(List.of(
                svc("Saç Kesim", 45,  "250", ServiceCategory.HAIR, false, List.of()),
                svc("Fön",       30,  "150", ServiceCategory.HAIR, false, List.of()),
                svc("Saç Boyama",120, "800", ServiceCategory.HAIR, false, List.of()),
                svc("Cilt Bakımı",60, "500", ServiceCategory.SKIN, true,  List.of(cilt1, cilt2)),
                svc("Protez Tırnak",90,"400",ServiceCategory.NAIL, true,  List.of(tirnak1, tirnak2)),
                svc("Manikür",   45, "200", ServiceCategory.NAIL, true,  List.of(tirnak1, tirnak2)),
                svc("Lazer Epilasyon",30,"600",ServiceCategory.LASER,true,List.of(lazerO, lazerC))
        ));
        all.forEach(s -> s.setCreatedAt(now));
        return serviceRepository.saveAll(all);
    }

    private ServiceDefinition svc(String name, int duration, String price, ServiceCategory cat,
                                   boolean requiresResource, List<Long> resourceIds) {
        return ServiceDefinition.builder()
                .salonId(DEFAULT_SALON_ID)
                .name(name).durationMinutes(duration).basePrice(new BigDecimal(price))
                .category(cat).requiresResource(requiresResource).active(true)
                .requiredResourceIds(new ArrayList<>(resourceIds))
                .build();
    }

    private void seedCustomers() {
        LocalDateTime now = LocalDateTime.now();
        List<Customer> all = List.of(
                cust("Merve", "Aksoy",  "0532 111 2233", "merve@mail.com", "VIP müşteri"),
                cust("Selin", "Yıldız", "0533 222 3344", "selin@mail.com", "Boya alerjisi var"),
                cust("Deniz", "Öztürk", "0534 333 4455", "deniz@mail.com", ""),
                cust("Ceren", "Arslan", "0535 444 5566", "ceren@mail.com", "Kahvesi sade"),
                cust("Büşra", "Koç",    "0536 555 6677", "busra@mail.com", "Zeynep hanımı istiyor")
        );
        all.forEach(c -> { c.setCreatedAt(now); c.setUpdatedAt(now); });
        customerRepository.saveAll(all);
    }

    private Customer cust(String fn, String ln, String phone, String email, String notes) {
        return Customer.builder().salonId(DEFAULT_SALON_ID).firstName(fn).lastName(ln).phone(phone).email(email).notes(notes).build();
    }

    private void seedWorkingHours(List<Staff> staff) {
        List<WorkingHours> all = new ArrayList<>();
        // Sadece SPECIALIST'ler için (ilk 4)
        for (int i = 0; i < 4 && i < staff.size(); i++) {
            Long staffId = staff.get(i).getId();
            for (DayOfWeek dow : DayOfWeek.values()) {
                boolean dayOff = (dow == DayOfWeek.SUNDAY);
                all.add(WorkingHours.builder()
                        .salonId(DEFAULT_SALON_ID)
                        .staffId(staffId).dayOfWeek(dow)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(18, 0))
                        .dayOff(dayOff).build());
            }
        }
        workingHoursRepository.saveAll(all);
    }

    private void seedAppointments(List<Staff> staff, List<ServiceDefinition> services) {
        // staff: 0=Ayşe, 1=Fatma, 2=Elif, 3=Zeynep
        // services: 0=Saç Kesim, 1=Fön, 2=Saç Boyama, 3=Cilt, 4=Protez Tırnak, 5=Manikür, 6=Lazer
        Long ayse = staff.get(0).getId();
        Long fatma = staff.get(1).getId();
        Long zeynep = staff.get(3).getId();

        Long sacKesim = services.get(0).getId();
        Long sacBoya = services.get(2).getId();
        Long manikur = services.get(5).getId();

        LocalDate today = LocalDate.now();

        mkAppt("Merve Aksoy", "0532 111 2233", ayse, sacKesim,
                today.atTime(9, 0), 45, AppointmentStatus.COMPLETED, "250", "Düzenli müşteri",
                List.of(), List.of(flag(FlagType.VIP, "VIP müşteri", "⭐")));

        mkAppt("Selin Yıldız", "0533 222 3344", ayse, sacBoya,
                today.atTime(10, 0), 120, AppointmentStatus.COMPLETED, "800", "",
                List.of(), List.of(flag(FlagType.ALLERGY, "Boya alerjisi — test yapıldı", "⚠️")));

        // ... (kısaltma yok — diğerleri de aynı şekilde)
        LocalDate tomorrow = today.plusDays(1);
        if (tomorrow.getDayOfWeek() != DayOfWeek.SUNDAY) {
            mkAppt("Aylin Şahin", "0537 666 7788", ayse, sacKesim,
                    tomorrow.atTime(9, 30), 45, AppointmentStatus.SCHEDULED, "250", "",
                    List.of(), List.of());
            mkAppt("Nur Yılmaz", "0538 777 8899", fatma, manikur,
                    tomorrow.atTime(10, 0), 45, AppointmentStatus.SCHEDULED, "200", "",
                    List.of(), List.of());
            mkAppt("Pınar Kara", "0539 888 9900", zeynep, sacBoya,
                    tomorrow.atTime(14, 0), 120, AppointmentStatus.SCHEDULED, "800", "",
                    List.of(), List.of());
        }

        LocalDate nextMon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        mkAppt("Selin Yıldız", "0533 222 3344", ayse, sacBoya,
                nextMon.atTime(10, 0), 120, AppointmentStatus.SCHEDULED, "800", "",
                List.of(), List.of(flag(FlagType.ALLERGY, "Boya alerjisi", "⚠️")));
    }

    private AppointmentFlag flag(FlagType type, String value, String icon) {
        return AppointmentFlag.builder().flagType(type).flagValue(value).icon(icon).build();
    }

    private void mkAppt(String customerName, String customerPhone, Long staffId, Long serviceId,
                         LocalDateTime start, int durationMinutes, AppointmentStatus status,
                         String price, String note, List<Long> resourceIds, List<AppointmentFlag> flags) {
        BigDecimal bp = new BigDecimal(price);
        LocalDateTime now = LocalDateTime.now();
        Appointment a = Appointment.builder()
                .salonId(DEFAULT_SALON_ID)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .staffId(staffId)
                .serviceId(serviceId)
                .startTime(start)
                .endTime(start.plusMinutes(durationMinutes))
                .status(status)
                .basePrice(bp)
                .adjustment(BigDecimal.ZERO)
                .adjustmentNote("")
                .finalPrice(bp)
                .internalNote(note)
                .createdAt(now)
                .updatedAt(now)
                .resourceIds(new ArrayList<>(resourceIds))
                .flags(new ArrayList<>())
                .build();
        // Flag'leri bağla (bidirectional)
        for (AppointmentFlag f : flags) {
            f.setAppointment(a);
            a.getFlags().add(f);
        }
        Appointment saved = appointmentRepository.save(a);
        log.trace("Seeded appointment id={}", saved.getId());
    }
}
