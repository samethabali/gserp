package com.gserp.model;

import com.gserp.model.enums.AppointmentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {
    private Long id;

    // ─── Customer (free-text, not a separate entity) ───
    private String customerName;
    private String customerPhone;

    private Long staffId;
    private Long serviceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AppointmentStatus status;

    // ─── Pricing ───
    private BigDecimal basePrice;
    private BigDecimal adjustment;       // +/- ek ücret veya indirim
    private String adjustmentNote;       // "ek boya", "VIP indirim" vb.
    private BigDecimal finalPrice;

    private String internalNote;
    private String cancellationReason;   // İptal/no-show nedeni

    // ─── Session (multi-session like laser) ───
    private String sessionGroupId;       // UUID — aynı gruptaki seansları ilişkilendirir
    private Integer sessionNumber;        // 1, 2, 3...
    private Integer totalSessions;        // toplam seans sayısı

    private int version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** IDs of resources locked for this appointment */
    @Builder.Default
    private List<Long> resourceIds = new ArrayList<>();

    /** Quick flags/sticky notes */
    @Builder.Default
    private List<AppointmentFlag> flags = new ArrayList<>();
}
