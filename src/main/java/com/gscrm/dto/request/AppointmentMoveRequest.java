package com.gscrm.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentMoveRequest {

    private Long appointmentId;

    private Long newStaffId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime newStartTime;

    /** For optimistic locking */
    private int version;
}
