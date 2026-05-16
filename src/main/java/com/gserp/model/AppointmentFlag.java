package com.gserp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gserp.model.enums.FlagType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "appointment_flag")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "appointment")
@EqualsAndHashCode(exclude = "appointment")
public class AppointmentFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_type", nullable = false, length = 32)
    private FlagType flagType;

    @Column(name = "flag_value")
    private String flagValue;

    private String icon;

    /** Convenience helper for callers that previously used appointmentId */
    @Transient
    public Long getAppointmentId() {
        return appointment != null ? appointment.getId() : null;
    }
}
