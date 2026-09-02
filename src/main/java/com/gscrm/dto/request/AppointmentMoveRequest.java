package com.gscrm.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentMoveRequest {

    private Long appointmentId;

    @NotNull(message = "Uzman seçilmelidir")
    private Long newStaffId;

    @NotNull(message = "Yeni başlangıç saati girilmelidir")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime newStartTime;

    /** For optimistic locking */
    private int version;
}
