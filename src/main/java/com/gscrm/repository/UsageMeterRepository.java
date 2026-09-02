package com.gscrm.repository;

import com.gscrm.model.UsageMeter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsageMeterRepository extends JpaRepository<UsageMeter, Long> {

    Optional<UsageMeter> findByOrganizationIdAndSalonIdAndMetricAndPeriod(
            Long organizationId, Long salonId, String metric, String period);

    int countByOrganizationIdAndMetricAndPeriod(Long organizationId, String metric, String period);
}
