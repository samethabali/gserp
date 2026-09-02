package com.gscrm.dto.response;

import com.gscrm.model.enums.PaymentMethod;
import com.gscrm.model.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private Long id;
    private Long appointmentId;
    private String customerName;
    private String customerPhone;
    private BigDecimal amount;
    private PaymentMethod method;
    private PaymentStatus status;
    private String deferredNote;
    private LocalDateTime collectedAt;
    private LocalDateTime createdAt;
}
