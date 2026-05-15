package com.gserp.store;

import com.gserp.model.*;
import com.gserp.model.enums.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory data store with demo data.
 * Replace with JPA repositories for production.
 */
@Component
public class MockDataStore {

    private final Map<Long, Staff> staffMap = new ConcurrentHashMap<>();
    private final Map<Long, Resource> resourceMap = new ConcurrentHashMap<>();
    private final Map<Long, ServiceDefinition> serviceMap = new ConcurrentHashMap<>();
    private final Map<Long, Customer> customerMap = new ConcurrentHashMap<>();
    private final Map<Long, Appointment> appointmentMap = new ConcurrentHashMap<>();
    private final Map<Long, WorkingHours> workingHoursMap = new ConcurrentHashMap<>();
    private final Map<Long, WaitlistEntry> waitlistMap = new ConcurrentHashMap<>();
    private final List<AuditLogEntry> auditLog = Collections.synchronizedList(new ArrayList<>());

    private final AtomicLong staffSeq = new AtomicLong(100);
    private final AtomicLong resourceSeq = new AtomicLong(200);
    private final AtomicLong serviceSeq = new AtomicLong(300);
    private final AtomicLong customerSeq = new AtomicLong(400);
    private final AtomicLong appointmentSeq = new AtomicLong(500);
    private final AtomicLong whSeq = new AtomicLong(600);
    private final AtomicLong flagSeq = new AtomicLong(700);
    private final AtomicLong auditSeq = new AtomicLong(800);
    private final AtomicLong waitlistSeq = new AtomicLong(900);

    @PostConstruct
    public void init() {
        initStaff();
        initResources();
        initServices();
        initCustomers();
        initWorkingHours();
        initAppointments();
    }

    // ─────────────────────── STAFF ───────────────────────

    private void initStaff() {
        addStaff(Staff.builder().name("Ayşe Yılmaz").phone("0532 100 0001").email("ayse@salon.com")
                .role(StaffRole.SPECIALIST).colorHex("#9b59b6").active(true).build());
        addStaff(Staff.builder().name("Fatma Demir").phone("0532 100 0002").email("fatma@salon.com")
                .role(StaffRole.SPECIALIST).colorHex("#e91e8c").active(true).build());
        addStaff(Staff.builder().name("Elif Kaya").phone("0532 100 0003").email("elif@salon.com")
                .role(StaffRole.SPECIALIST).colorHex("#f39c12").active(true).build());
        addStaff(Staff.builder().name("Zeynep Çelik").phone("0532 100 0004").email("zeynep@salon.com")
                .role(StaffRole.SPECIALIST).colorHex("#2ecc71").active(true).build());
        addStaff(Staff.builder().name("Banko").phone("0532 100 0000").email("banko@salon.com")
                .role(StaffRole.RECEPTIONIST).colorHex("#3498db").active(true).build());
    }

    public Staff addStaff(Staff s) {
        long id = staffSeq.incrementAndGet();
        s.setId(id);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        staffMap.put(id, s);
        return s;
    }

    // ─────────────────────── RESOURCES ───────────────────────

    private void initResources() {
        addResource(Resource.builder().name("Cilt Bakım Odası 1").resourceType(ResourceType.ROOM).capacity(1).active(true).build());
        addResource(Resource.builder().name("Cilt Bakım Odası 2").resourceType(ResourceType.ROOM).capacity(1).active(true).build());
        addResource(Resource.builder().name("Lazer Odası").resourceType(ResourceType.ROOM).capacity(1).active(true).build());
        addResource(Resource.builder().name("Lazer Cihazı").resourceType(ResourceType.DEVICE).capacity(1).active(true).build());
        addResource(Resource.builder().name("Tırnak Masası 1").resourceType(ResourceType.EQUIPMENT).capacity(1).active(true).build());
        addResource(Resource.builder().name("Tırnak Masası 2").resourceType(ResourceType.EQUIPMENT).capacity(1).active(true).build());
    }

    public Resource addResource(Resource r) {
        long id = resourceSeq.incrementAndGet();
        r.setId(id);
        r.setCreatedAt(LocalDateTime.now());
        resourceMap.put(id, r);
        return r;
    }

    // ─────────────────────── SERVICES ───────────────────────

    private void initServices() {
        addService(ServiceDefinition.builder().name("Saç Kesim").durationMinutes(45)
                .basePrice(new BigDecimal("250")).category(ServiceCategory.HAIR)
                .requiresResource(false).active(true).build());
        addService(ServiceDefinition.builder().name("Fön").durationMinutes(30)
                .basePrice(new BigDecimal("150")).category(ServiceCategory.HAIR)
                .requiresResource(false).active(true).build());
        addService(ServiceDefinition.builder().name("Saç Boyama").durationMinutes(120)
                .basePrice(new BigDecimal("800")).category(ServiceCategory.HAIR)
                .requiresResource(false).active(true).build());

        addService(ServiceDefinition.builder().name("Cilt Bakımı").durationMinutes(60)
                .basePrice(new BigDecimal("500")).category(ServiceCategory.SKIN)
                .requiresResource(true).active(true).requiredResourceIds(new ArrayList<>(List.of(201L, 202L))).build());

        addService(ServiceDefinition.builder().name("Protez Tırnak").durationMinutes(90)
                .basePrice(new BigDecimal("400")).category(ServiceCategory.NAIL)
                .requiresResource(true).active(true).requiredResourceIds(new ArrayList<>(List.of(205L, 206L))).build());
        addService(ServiceDefinition.builder().name("Manikür").durationMinutes(45)
                .basePrice(new BigDecimal("200")).category(ServiceCategory.NAIL)
                .requiresResource(true).active(true).requiredResourceIds(new ArrayList<>(List.of(205L, 206L))).build());

        addService(ServiceDefinition.builder().name("Lazer Epilasyon").durationMinutes(30)
                .basePrice(new BigDecimal("600")).category(ServiceCategory.LASER)
                .requiresResource(true).active(true).requiredResourceIds(new ArrayList<>(List.of(203L, 204L))).build());
    }

    public ServiceDefinition addService(ServiceDefinition s) {
        long id = serviceSeq.incrementAndGet();
        s.setId(id);
        s.setCreatedAt(LocalDateTime.now());
        serviceMap.put(id, s);
        return s;
    }

    // ─────────────────────── CUSTOMERS (kept for backward compat, not used in appointments) ───────────────────────

    private void initCustomers() {
        addCustomer(Customer.builder().firstName("Merve").lastName("Aksoy")
                .phone("0532 111 2233").email("merve@mail.com").notes("VIP müşteri").build());
        addCustomer(Customer.builder().firstName("Selin").lastName("Yıldız")
                .phone("0533 222 3344").email("selin@mail.com").notes("Boya alerjisi var").build());
        addCustomer(Customer.builder().firstName("Deniz").lastName("Öztürk")
                .phone("0534 333 4455").email("deniz@mail.com").notes("").build());
        addCustomer(Customer.builder().firstName("Ceren").lastName("Arslan")
                .phone("0535 444 5566").email("ceren@mail.com").notes("Kahvesi sade").build());
        addCustomer(Customer.builder().firstName("Büşra").lastName("Koç")
                .phone("0536 555 6677").email("busra@mail.com").notes("Zeynep hanımı istiyor").build());
    }

    public Customer addCustomer(Customer c) {
        long id = customerSeq.incrementAndGet();
        c.setId(id);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        customerMap.put(id, c);
        return c;
    }

    // ─────────────────────── WORKING HOURS ───────────────────────

    private void initWorkingHours() {
        for (long staffId = 101; staffId <= 104; staffId++) {
            for (DayOfWeek dow : DayOfWeek.values()) {
                boolean dayOff = (dow == DayOfWeek.SUNDAY);
                long id = whSeq.incrementAndGet();
                workingHoursMap.put(id, WorkingHours.builder()
                        .id(id).staffId(staffId).dayOfWeek(dow)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(18, 0))
                        .dayOff(dayOff).build());
            }
        }
    }

    // ─────────────────────── APPOINTMENTS (multi-day demo data) ───────────────────────

    private void initAppointments() {
        LocalDate today = LocalDate.now();

        // ── TODAY ──
        mkAppt("Merve Aksoy", "0532 111 2233", 101L, 301L,
                today.atTime(9, 0), 45, AppointmentStatus.COMPLETED, "250", "Düzenli müşteri",
                List.of(), flag(FlagType.VIP, "VIP müşteri", "⭐"));

        mkAppt("Selin Yıldız", "0533 222 3344", 101L, 303L,
                today.atTime(10, 0), 120, AppointmentStatus.COMPLETED, "800", "",
                List.of(), flag(FlagType.ALLERGY, "Boya alerjisi — test yapıldı", "⚠️"));

        mkAppt("Deniz Öztürk", "0534 333 4455", 102L, 304L,
                today.atTime(10, 0), 60, AppointmentStatus.IN_PROGRESS, "500", "Cilt Bakım Odası 1",
                List.of(201L));

        mkAppt("Ceren Arslan", "0535 444 5566", 103L, 305L,
                today.atTime(11, 30), 90, AppointmentStatus.SCHEDULED, "400", "",
                List.of(205L), flag(FlagType.DRINK, "Kahvesi sade", "☕"));

        mkAppt("Büşra Koç", "0536 555 6677", 104L, 307L,
                today.atTime(13, 0), 30, AppointmentStatus.SCHEDULED, "600", "",
                List.of(203L, 204L), flag(FlagType.PREFERENCE, "Zeynep Hanım'ı istiyor", "🎯"));

        mkAppt("Merve Aksoy", "0532 111 2233", 101L, 302L,
                today.atTime(14, 0), 30, AppointmentStatus.SCHEDULED, "150", "",
                List.of(), flag(FlagType.VIP, "VIP müşteri", "⭐"));

        mkAppt("Selin Yıldız", "0533 222 3344", 102L, 304L,
                today.atTime(15, 0), 60, AppointmentStatus.NO_SHOW, "500", "Gelmedi — arandı, cevap yok",
                List.of(202L));

        // ── TOMORROW ──
        LocalDate tomorrow = today.plusDays(1);
        if (tomorrow.getDayOfWeek() != DayOfWeek.SUNDAY) {
            mkAppt("Aylin Şahin", "0537 666 7788", 101L, 301L,
                    tomorrow.atTime(9, 30), 45, AppointmentStatus.SCHEDULED, "250", "",
                    List.of());

            mkAppt("Nur Yılmaz", "0538 777 8899", 102L, 306L,
                    tomorrow.atTime(10, 0), 45, AppointmentStatus.SCHEDULED, "200", "",
                    List.of(205L));

            mkAppt("Deniz Öztürk", "0534 333 4455", 103L, 307L,
                    tomorrow.atTime(11, 0), 30, AppointmentStatus.SCHEDULED, "600", "Lazer seans 2/8",
                    List.of(203L, 204L));

            mkAppt("Pınar Kara", "0539 888 9900", 104L, 303L,
                    tomorrow.atTime(14, 0), 120, AppointmentStatus.SCHEDULED, "800", "",
                    List.of());
        }

        // ── DAY AFTER TOMORROW ──
        LocalDate day2 = today.plusDays(2);
        if (day2.getDayOfWeek() != DayOfWeek.SUNDAY) {
            mkAppt("Merve Aksoy", "0532 111 2233", 102L, 304L,
                    day2.atTime(10, 0), 60, AppointmentStatus.SCHEDULED, "500", "",
                    List.of(201L), flag(FlagType.VIP, "VIP müşteri", "⭐"));

            mkAppt("Esra Polat", "0530 999 0011", 101L, 301L,
                    day2.atTime(11, 0), 45, AppointmentStatus.SCHEDULED, "250", "",
                    List.of());

            mkAppt("Ceren Arslan", "0535 444 5566", 103L, 305L,
                    day2.atTime(13, 0), 90, AppointmentStatus.SCHEDULED, "400", "",
                    List.of(206L), flag(FlagType.DRINK, "Kahvesi sade", "☕"));
        }

        // ── 3 DAYS LATER ──
        LocalDate day3 = today.plusDays(3);
        if (day3.getDayOfWeek() != DayOfWeek.SUNDAY) {
            mkAppt("Büşra Koç", "0536 555 6677", 104L, 307L,
                    day3.atTime(10, 0), 30, AppointmentStatus.SCHEDULED, "600", "Lazer seans 3/8",
                    List.of(203L, 204L), flag(FlagType.PREFERENCE, "Zeynep Hanım'ı istiyor", "🎯"));

            mkAppt("Aylin Şahin", "0537 666 7788", 102L, 304L,
                    day3.atTime(14, 0), 60, AppointmentStatus.SCHEDULED, "500", "",
                    List.of(202L));
        }

        // ── NEXT WEEK ──
        LocalDate nextMon = today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
        mkAppt("Selin Yıldız", "0533 222 3344", 101L, 303L,
                nextMon.atTime(10, 0), 120, AppointmentStatus.SCHEDULED, "800", "",
                List.of(), flag(FlagType.ALLERGY, "Boya alerjisi", "⚠️"));

        mkAppt("Deniz Öztürk", "0534 333 4455", 103L, 307L,
                nextMon.atTime(13, 0), 30, AppointmentStatus.SCHEDULED, "600", "Lazer seans 3/8",
                List.of(203L, 204L));

        mkAppt("Pınar Kara", "0539 888 9900", 104L, 301L,
                nextMon.plusDays(1).atTime(9, 0), 45, AppointmentStatus.SCHEDULED, "250", "",
                List.of());

        mkAppt("Nur Yılmaz", "0538 777 8899", 102L, 306L,
                nextMon.plusDays(2).atTime(11, 0), 45, AppointmentStatus.SCHEDULED, "200", "",
                List.of(206L));
    }

    private AppointmentFlag flag(FlagType type, String value, String icon) {
        return AppointmentFlag.builder()
                .id(flagSeq.incrementAndGet())
                .flagType(type).flagValue(value).icon(icon).build();
    }

    @SafeVarargs
    private void mkAppt(String customerName, String customerPhone, Long staffId, Long serviceId,
                         LocalDateTime start, int durationMinutes, AppointmentStatus status,
                         String price, String note, List<Long> resourceIds, AppointmentFlag... flags) {
        long id = appointmentSeq.incrementAndGet();
        BigDecimal bp = new BigDecimal(price);
        Appointment a = Appointment.builder()
                .id(id)
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
                .version(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .flags(new ArrayList<>(List.of(flags)))
                .resourceIds(new ArrayList<>(resourceIds))
                .build();
        appointmentMap.put(id, a);
    }

    // ═══════════════════════ PUBLIC ACCESSORS ═══════════════════════

    // Staff
    public Map<Long, Staff> getStaffMap() { return staffMap; }
    public Optional<Staff> findStaffById(Long id) { return Optional.ofNullable(staffMap.get(id)); }
    public List<Staff> findAllStaff() { return new ArrayList<>(staffMap.values()); }
    public List<Staff> findActiveSpecialists() {
        return staffMap.values().stream()
                .filter(s -> s.isActive() && s.getRole() == StaffRole.SPECIALIST)
                .toList();
    }

    // Resources
    public Optional<Resource> findResourceById(Long id) { return Optional.ofNullable(resourceMap.get(id)); }
    public List<Resource> findAllResources() { return new ArrayList<>(resourceMap.values()); }
    public List<Resource> findActiveResources() {
        return resourceMap.values().stream().filter(Resource::isActive).toList();
    }

    // Services
    public Optional<ServiceDefinition> findServiceById(Long id) { return Optional.ofNullable(serviceMap.get(id)); }
    public List<ServiceDefinition> findAllServices() { return new ArrayList<>(serviceMap.values()); }
    public List<ServiceDefinition> findActiveServices() {
        return serviceMap.values().stream().filter(ServiceDefinition::isActive).toList();
    }

    // Customers (still here for reference, not used by appointments)
    public Optional<Customer> findCustomerById(Long id) { return Optional.ofNullable(customerMap.get(id)); }
    public List<Customer> findAllCustomers() { return new ArrayList<>(customerMap.values()); }

    // Appointments
    public Optional<Appointment> findAppointmentById(Long id) { return Optional.ofNullable(appointmentMap.get(id)); }
    public List<Appointment> findAllAppointments() { return new ArrayList<>(appointmentMap.values()); }

    public List<Appointment> findAppointmentsByDate(LocalDate date) {
        return appointmentMap.values().stream()
                .filter(a -> a.getStartTime().toLocalDate().equals(date))
                .sorted(Comparator.comparing(Appointment::getStartTime))
                .toList();
    }

    public List<Appointment> findAppointmentsByStaffAndDateRange(Long staffId, LocalDateTime start, LocalDateTime end) {
        return appointmentMap.values().stream()
                .filter(a -> a.getStaffId().equals(staffId))
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .filter(a -> a.getStartTime().isBefore(end) && a.getEndTime().isAfter(start))
                .toList();
    }

    public List<Appointment> findAppointmentsByResourceAndDateRange(Long resourceId, LocalDateTime start, LocalDateTime end) {
        return appointmentMap.values().stream()
                .filter(a -> a.getResourceIds().contains(resourceId))
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .filter(a -> a.getStartTime().isBefore(end) && a.getEndTime().isAfter(start))
                .toList();
    }

    public Appointment saveAppointment(Appointment a) {
        if (a.getId() == null) {
            a.setId(appointmentSeq.incrementAndGet());
            a.setCreatedAt(LocalDateTime.now());
        }
        a.setUpdatedAt(LocalDateTime.now());
        appointmentMap.put(a.getId(), a);
        return a;
    }

    public void deleteAppointment(Long id) { appointmentMap.remove(id); }

    // Working Hours
    public List<WorkingHours> findWorkingHoursByStaff(Long staffId) {
        return workingHoursMap.values().stream()
                .filter(wh -> wh.getStaffId().equals(staffId))
                .toList();
    }

    // Audit Log
    public void addAuditLog(AuditLogEntry entry) {
        entry.setId(auditSeq.incrementAndGet());
        entry.setCreatedAt(LocalDateTime.now());
        auditLog.add(entry);
    }

    public List<AuditLogEntry> getRecentAuditLogs(int limit) {
        int size = auditLog.size();
        int from = Math.max(0, size - limit);
        return new ArrayList<>(auditLog.subList(from, size));
    }

    public long nextFlagId() { return flagSeq.incrementAndGet(); }

    // Update helpers
    public Staff updateStaff(Staff s) { s.setUpdatedAt(LocalDateTime.now()); staffMap.put(s.getId(), s); return s; }
    public Customer updateCustomer(Customer c) { c.setUpdatedAt(LocalDateTime.now()); customerMap.put(c.getId(), c); return c; }
    public void deleteCustomer(Long id) { customerMap.remove(id); }
    public ServiceDefinition updateService(ServiceDefinition s) { serviceMap.put(s.getId(), s); return s; }
    public Resource updateResource(Resource r) { resourceMap.put(r.getId(), r); return r; }

    // Waitlist
    public WaitlistEntry saveWaitlistEntry(WaitlistEntry e) {
        if (e.getId() == null) e.setId(waitlistSeq.incrementAndGet());
        waitlistMap.put(e.getId(), e);
        return e;
    }
    public Optional<WaitlistEntry> findWaitlistEntryById(Long id) { return Optional.ofNullable(waitlistMap.get(id)); }
    public List<WaitlistEntry> findActiveWaitlistEntries() {
        return waitlistMap.values().stream().filter(e -> !e.isFulfilled()).toList();
    }
    public List<WaitlistEntry> findAllWaitlistEntries() { return new ArrayList<>(waitlistMap.values()); }
    public void deleteWaitlistEntry(Long id) { waitlistMap.remove(id); }
}
