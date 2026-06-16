package com.gserp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantProvisionResponse {
    private Long organizationId;
    private Long salonId;
    private String salonSlug;
    private Long adminUserId;
    private String onboardingStep;
    private LocalDateTime trialEnd;
}
