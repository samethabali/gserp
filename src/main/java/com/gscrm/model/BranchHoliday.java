package com.gscrm.model;

import com.gscrm.tenant.TenantEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.time.LocalDate;

@Entity
@Filter(name = "tenantFilter", condition = "salon_id = :salonId")
@Table(name = "branch_holiday")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchHoliday implements TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(length = 255)
    private String reason;
}
