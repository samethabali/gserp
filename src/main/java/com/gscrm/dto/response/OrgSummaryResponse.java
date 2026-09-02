package com.gscrm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OrgSummaryResponse {
    private Long organizationId;
    private String organizationName;
    private int salonCount;
    private int totalAppointmentsToday;
    private BigDecimal totalRevenueToday;
    private String topSalonName;
    private List<SalonSummary> salons;

    @Data
    @Builder
    public static class SalonSummary {
        private Long salonId;
        private String slug;
        private String name;
        private int appointmentsToday;
        private BigDecimal revenueToday;
        private int revenueSharePercent;
    }
}
