package com.gserp.model;

import com.gserp.model.enums.StaffRole;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Staff {
    private Long id;
    private String name;
    private String phone;
    private String email;
    private StaffRole role;
    private String colorHex;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
