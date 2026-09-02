package com.gscrm.repository;

import com.gscrm.model.UsageMeter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsageMeterRepository extends JpaRepository<UsageMeter, Long> {

    Optional<UsageMeter> findByOrganizationIdAndSalonIdAndMetricAndPeriod(
            Long organizationId, Long salonId, String metric, String period);

    int countByOrganizationIdAndMetricAndPeriod(Long organizationId, String metric, String period);

    List<UsageMeter> findByOrganizationIdAndPeriod(Long organizationId, String period);

    /** Belirli metriğin org+dönem toplamı (DB tarafında SUM). */
    @Query("select coalesce(sum(m.count), 0) from UsageMeter m "
            + "where m.organizationId = :orgId and m.metric = :metric and m.period = :period")
    long sumCount(@Param("orgId") Long organizationId,
                  @Param("metric") String metric,
                  @Param("period") String period);
}
