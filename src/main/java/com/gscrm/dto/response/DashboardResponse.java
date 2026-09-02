package com.gscrm.dto.response;

import com.gscrm.model.enums.AppointmentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private int totalAppointments;
    private int completedAppointments;
    private int inProgressAppointments;
    private int scheduledAppointments;
    private int cancelledAppointments;
    private int noShows;

    private BigDecimal totalRevenue;      // Tahakkuk: tamamlanan randevuların tutarı
    private BigDecimal collectedRevenue;  // Tahsilat: payment tablosuna işlenen tutar
    private BigDecimal uncollectedRevenue; // Tamamlandı ama tahsil edilmedi (tahakkuk − tahsilat)
    private BigDecimal expectedRevenue;   // Tüm randevuların toplamı

    private List<StaffPerformance> staffPerformance;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffPerformance {
        private Long staffId;
        private String staffName;
        private String staffColor;
        private int totalAppointments;
        private int completed;
        private int noShows;
        private BigDecimal revenue;
        private List<AppointmentDetail> appointments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppointmentDetail {
        private Long appointmentId;
        private String customerName;
        private String serviceName;
        private LocalDateTime startTime;
        private BigDecimal finalPrice;
        private AppointmentStatus status;
    }
}
