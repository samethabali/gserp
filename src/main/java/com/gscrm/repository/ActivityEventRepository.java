package com.gscrm.repository;

import com.gscrm.model.ActivityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {

    List<ActivityEvent> findBySalonIdOrderByCreatedAtDesc(Long salonId, Pageable pageable);

    List<ActivityEvent> findBySalonIdAndCustomerIdOrderByCreatedAtDesc(Long salonId, Long customerId, Pageable pageable);

    List<ActivityEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Kütük sayfasının filtreleri. Boş bırakılan her ölçüt kendi koşulunu düşürür,
     * böylece tek sorgu hem filtresiz hem filtreli listeyi karşılar.
     */
    @Query("""
            SELECT e FROM ActivityEvent e
            WHERE e.salonId = :salonId
              AND (:from IS NULL OR e.createdAt >= :from)
              AND (:to IS NULL OR e.createdAt <= :to)
              AND (:action IS NULL OR e.action = :action)
              AND (:username IS NULL OR LOWER(e.actorUsername) = LOWER(:username))
              AND (:query IS NULL OR LOWER(e.summary) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<ActivityEvent> search(@Param("salonId") Long salonId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               @Param("action") String action,
                               @Param("username") String username,
                               @Param("query") String query,
                               Pageable pageable);

    @Query("SELECT DISTINCT e.action FROM ActivityEvent e WHERE e.salonId = :salonId ORDER BY e.action")
    List<String> findDistinctActions(@Param("salonId") Long salonId);

    @Modifying
    @Query("DELETE FROM ActivityEvent e WHERE e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    /** Kiracı listesindeki "son işlem" kolonu; salon başına en yeni kayıt zamanı. */
    @Query("SELECT e.salonId, MAX(e.createdAt) FROM ActivityEvent e WHERE e.salonId IN :salonIds GROUP BY e.salonId")
    List<Object[]> findLastActivityGroupedBySalonIds(@Param("salonIds") java.util.Collection<Long> salonIds);
}
