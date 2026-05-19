package com.gserp.dto.request;

import com.gserp.model.enums.PaymentMethod;
import com.gserp.model.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentCreateRequest {

    @NotNull
    private Long appointmentId;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private PaymentMethod method;

    private PaymentStatus status = PaymentStatus.PAID;

    private String deferredNote;
}
