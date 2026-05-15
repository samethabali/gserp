package com.gserp.model;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    private Long id;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
