package com.gserp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RecentCustomerDto {
    private Long id;
    private String fullName;
    private String phone;
    private LocalDateTime lastVisit;
    private String lastServiceName;
    private String lastStaffName;
}
