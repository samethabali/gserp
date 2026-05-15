package com.gserp.model;

import com.gserp.model.enums.ResourceType;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resource {
    private Long id;
    private String name;
    private ResourceType resourceType;
    private int capacity;
    private boolean active;
    private LocalDateTime createdAt;
}
