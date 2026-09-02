package com.gscrm.service;

import com.gscrm.dto.response.DailyTrendDto;
import com.gscrm.dto.response.DashboardResponse;
import com.gscrm.dto.response.OrgSummaryResponse;
import com.gscrm.model.Appointment;
import com.gscrm.model.Organization;
import com.gscrm.model.Salon;
import com.gscrm.model.ServiceDefinition;
import com.gscrm.model.Staff;
import com.gscrm.model.enums.AppointmentStatus;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.ServiceDefinitionRepository;
import com.gscrm.repository.StaffRepository;
import com.gscrm.tenant.TenantContext;
import com.gscrm.tenant.TenantFilterSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final ServiceDefinitionRepository serviceDefinitionRepository;
    private final SalonRepository salonRepository;
    private final OrganizationRepository organizationRepository;
    private final TenantFilterSupport tenantFilterSupport;

    public DashboardResponse getDailySummary(LocalDate date) {
        Long salonId = TenantContext.requireSalonId();
        List<Appointment> appointments = appointmentRepository.findBySalonIdAndStartTimeBetween(
                salonId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());

        int total = appointments.size();
        int completed = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
        int inProgress = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.IN_PROGRESS).count();
        int scheduled = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED).count();
        int noShow = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();
        int cancelled = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();

        BigDecimal totalRevenue = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                .map(Appointment::getFinalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedRevenue = appointments.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .map(Appointment::getFinalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Hizmet adlarını tek seferde yükle (N+1 önlemi)
        Map<Long, String> serviceNames = serviceDefinitionRepository.findAll().stream()
                .collect(Collectors.toMap(ServiceDefinition::getId, ServiceDefinition::getName));

        // Staff performance — tüm ilgili personelleri tek sorguda yükle (N+1 önlemi)
        Map<Long, List<Appointment>> byStaff = appointments.stream()
                .collect(Collectors.groupingBy(Appointment::getStaffId));

        Map<Long, Staff> staffMap = staffRepository.findAllById(byStaff.keySet()).stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        List<DashboardResponse.StaffPerformance> staffPerf = new ArrayList<>();
        for (var entry : byStaff.entrySet()) {
            Staff staff = staffMap.get(entry.getKey());
            if (staff == null) continue;

            List<Appointment> staffAppts = entry.getValue();
            int sCompleted = (int) staffAppts.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
            int sNoShows = (int) staffAppts.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();
            BigDecimal sRevenue = staffAppts.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                    .map(Appointment::getFinalPrice)
                    .filter(p -> p != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<DashboardResponse.AppointmentDetail> details = staffAppts.stream()
                    .map(a -> DashboardResponse.AppointmentDetail.builder()
                            .appointmentId(a.getId())
                            .customerName(a.getCustomerName())
                            .serviceName(serviceNames.getOrDefault(a.getServiceId(), "-"))
                            .startTime(a.getStartTime())
                            .finalPrice(a.getFinalPrice())
                            .status(a.getStatus())
                            .build())
                    .sorted(java.util.Comparator.comparing(DashboardResponse.AppointmentDetail::getStartTime))
                    .collect(Collectors.toList());

            staffPerf.add(DashboardResponse.StaffPerformance.builder()
                    .staffId(staff.getId())
                    .staffName(staff.getName())
                    .staffColor(staff.getColorHex())
                    .totalAppointments(staffAppts.size())
                    .completed(sCompleted)
                    .noShows(sNoShows)
                    .revenue(sRevenue)
                    .appointments(details)
                    .build());
        }

        return DashboardResponse.builder()
                .totalAppointments(total)
                .completedAppointments(completed)
                .inProgressAppointments(inProgress)
                .scheduledAppointments(scheduled)
                .cancelledAppointments(cancelled)
                .noShows(noShow)
                .totalRevenue(totalRevenue)
                .expectedRevenue(expectedRevenue)
                .staffPerformance(staffPerf)
                .build();
    }

    public List<DailyTrendDto> getTrend(int days) {
        Long salonId = TenantContext.requireSalonId();
        LocalDate today = LocalDate.now();
        List<DailyTrendDto> result = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<Appointment> appts = appointmentRepository.findBySalonIdAndStartTimeBetween(
                    salonId, day.atStartOfDay(), day.plusDays(1).atStartOfDay());

            int tot = appts.size();
            int comp = (int) appts.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
            BigDecimal rev = appts.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                    .map(Appointment::getFinalPrice)
                    .filter(p -> p != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.add(DailyTrendDto.builder()
                    .date(day.toString())
                    .totalAppointments(tot)
                    .completed(comp)
                    .revenue(rev)
                    .build());
        }
        return result;
    }

    /**
     * Organizasyon geneli özet — tenant filtresinden muaf tek okuma yolu.
     *
     * <p>Filtre mevcut şubeye kısıtlar; bu rapor ise organizasyonun tüm şubelerini
     * gezer. Muafiyet güvenlidir çünkü {@code organizationId} çağıran tarafta
     * (OrganizationController) kullanıcının kendi organizasyonu olarak doğrulanır
     * ve aşağıdaki sorguların hepsi zaten salon bazında açıkça kapsamlıdır.
     */
    public OrgSummaryResponse getOrgSummary(Long organizationId) {
        return tenantFilterSupport.runUnfiltered(() -> buildOrgSummary(organizationId));
    }

    private OrgSummaryResponse buildOrgSummary(Long organizationId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organizasyon bulunamadı"));
        LocalDate today = LocalDate.now();
        List<Salon> salons = salonRepository.findByOrganizationIdAndActiveTrue(organizationId);

        List<OrgSummaryResponse.SalonSummary> salonSummaries = new ArrayList<>();
        int totalAppts = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (Salon salon : salons) {
            List<Appointment> appts = appointmentRepository.findBySalonIdAndStartTimeBetween(
                    salon.getId(), today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            BigDecimal rev = appts.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                    .map(Appointment::getFinalPrice)
                    .filter(p -> p != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalAppts += appts.size();
            totalRevenue = totalRevenue.add(rev);
            salonSummaries.add(OrgSummaryResponse.SalonSummary.builder()
                    .salonId(salon.getId())
                    .slug(salon.getSlug())
                    .name(salon.getName())
                    .appointmentsToday(appts.size())
                    .revenueToday(rev)
                    .build());
        }

        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            for (OrgSummaryResponse.SalonSummary s : salonSummaries) {
                int pct = s.getRevenueToday()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalRevenue, 0, java.math.RoundingMode.HALF_UP)
                        .intValue();
                s.setRevenueSharePercent(pct);
            }
        }

        String topSalon = salonSummaries.stream()
                .max(java.util.Comparator.comparing(OrgSummaryResponse.SalonSummary::getRevenueToday))
                .map(OrgSummaryResponse.SalonSummary::getName)
                .orElse(null);

        return OrgSummaryResponse.builder()
                .organizationId(organizationId)
                .organizationName(org.getName())
                .salonCount(salons.size())
                .totalAppointmentsToday(totalAppts)
                .totalRevenueToday(totalRevenue)
                .topSalonName(topSalon)
                .salons(salonSummaries)
                .build();
    }
}
