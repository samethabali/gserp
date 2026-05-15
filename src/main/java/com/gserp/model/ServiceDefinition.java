package com.gserp.model;

import com.gserp.model.enums.ServiceCategory;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDefinition {
    private Long id;
    private String name;
    private int durationMinutes;
    private BigDecimal basePrice;
    private ServiceCategory category;
    private boolean requiresResource;
    private boolean active;
    private LocalDateTime createdAt;

    /** IDs of resources required for this service */
    @Builder.Default
    private List<Long> requiredResourceIds = new ArrayList<>();
}
