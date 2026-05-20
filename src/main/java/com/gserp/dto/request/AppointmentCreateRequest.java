package com.gserp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentCreateRequest {

    @NotBlank(message = "Müşteri adı girilmelidir")
    private String customerName;

    private String customerPhone;

    @NotNull(message = "Uzman seçilmelidir")
    private Long staffId;

    @NotNull(message = "Hizmet seçilmelidir")
    private Long serviceId;

    @NotNull(message = "Başlangıç saati girilmelidir")
    private LocalDateTime startTime;

    private BigDecimal finalPrice;
    private BigDecimal adjustment;
    private String adjustmentNote;
    private String internalNote;
    private List<FlagRequest> flags;

    // ─── Session support ───
    private Integer numberOfSessions;       // null veya 1 = tek randevu; >1 = çoklu seans
    private DayOfWeek preferredDayOfWeek;   // tercih edilen gün (seans sistemi için)

    // ─── Campaign support ───
    private String couponCode;              // müşteri portalından kupon kodu
}
