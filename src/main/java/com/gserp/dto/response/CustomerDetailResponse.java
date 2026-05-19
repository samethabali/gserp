package com.gserp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CustomerDetailResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phone;
    private String email;
    private String notes;
    private BigDecimal balance;
    private LocalDateTime createdAt;
    private List<AppointmentResponse> pastAppointments;
    private List<AppointmentResponse> upcomingAppointments;
    private List<PaymentResponse> payments;
    private List<ProductSaleResponse> productSales;
}
