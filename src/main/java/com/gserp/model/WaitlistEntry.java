package com.gserp.model;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntry {
    private Long id;
    private String customerName;
    private String customerPhone;
    private Long serviceId;
    private Long preferredStaffId;
    private LocalDate preferredDate;
    private LocalTime preferredTime;
    private String notes;
    private boolean fulfilled;
    private LocalDateTime createdAt;
}
