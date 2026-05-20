package com.gserp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DailyTrendDto {
    private String date;
    private int totalAppointments;
    private int completed;
    private BigDecimal revenue;
}
