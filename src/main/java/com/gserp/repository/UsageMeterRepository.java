package com.gserp.repository;

import com.gserp.model.UsageMeter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsageMeterRepository extends JpaRepository<UsageMeter, Long> {

    Optional<UsageMeter> findByOrganizationIdAndSalonIdAndMetricAndPeriod(
            Long organizationId, Long salonId, String metric, String period);

    int countByOrganizationIdAndMetricAndPeriod(Long organizationId, String metric, String period);
}
