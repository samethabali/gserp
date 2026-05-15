package com.gserp.dto.response;

import com.gserp.model.AppointmentFlag;
import com.gserp.model.enums.AppointmentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {
    private Long id;

    // Customer (free text)
    private String customerName;
    private String customerPhone;

    // Staff
    private Long staffId;
    private String staffName;
    private String staffColor;

    // Service
    private Long serviceId;
    private String serviceName;
    private int durationMinutes;

    // Time
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AppointmentStatus status;

    // Pricing
    private BigDecimal basePrice;
    private BigDecimal adjustment;
    private String adjustmentNote;
    private BigDecimal finalPrice;

    // Details
    private String internalNote;
    private String cancellationReason;
    private int version;

    // Session
    private String sessionGroupId;
    private Integer sessionNumber;
    private Integer totalSessions;

    // Flags & Resources
    private List<AppointmentFlag> flags;
    private List<Long> resourceIds;
}
