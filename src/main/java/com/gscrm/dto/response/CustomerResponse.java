package com.gscrm.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CustomerResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phone;
    private String email;
    private String notes;
    private BigDecimal balance;
    private int totalAppointments;
    private int upcomingAppointments;
    private LocalDateTime createdAt;
}
