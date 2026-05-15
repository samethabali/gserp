package com.gserp.model;

import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingHours {
    private Long id;
    private Long staffId;
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean dayOff;
}
