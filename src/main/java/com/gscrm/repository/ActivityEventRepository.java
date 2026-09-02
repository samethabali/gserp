package com.gscrm.repository;

import com.gscrm.model.ActivityEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {

    List<ActivityEvent> findBySalonIdOrderByCreatedAtDesc(Long salonId, Pageable pageable);

    List<ActivityEvent> findBySalonIdAndCustomerIdOrderByCreatedAtDesc(Long salonId, Long customerId, Pageable pageable);
}
