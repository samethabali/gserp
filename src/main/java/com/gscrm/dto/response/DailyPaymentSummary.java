package com.gscrm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class DailyPaymentSummary {
    private LocalDate date;
    private BigDecimal cashTotal;
    private BigDecimal cardTotal;
    private BigDecimal deferredTotal;
    private BigDecimal grandTotal;
    private int paymentCount;
}
