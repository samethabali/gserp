package com.gscrm.repository;

import com.gscrm.model.Salon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalonRepository extends JpaRepository<Salon, Long> {

    Optional<Salon> findBySlugAndActiveTrue(String slug);

    java.util.List<Salon> findByOrganizationIdAndActiveTrue(Long organizationId);
}
