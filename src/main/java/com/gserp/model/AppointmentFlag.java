package com.gserp.model;

import com.gserp.model.enums.FlagType;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentFlag {
    private Long id;
    private Long appointmentId;
    private FlagType flagType;
    private String flagValue;
    private String icon;
}
