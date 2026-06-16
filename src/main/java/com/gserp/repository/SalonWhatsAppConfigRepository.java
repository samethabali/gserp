package com.gserp.repository;

import com.gserp.model.SalonWhatsAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalonWhatsAppConfigRepository extends JpaRepository<SalonWhatsAppConfig, Long> {
    Optional<SalonWhatsAppConfig> findBySalonId(Long salonId);
    Optional<SalonWhatsAppConfig> findByPhoneNumberId(String phoneNumberId);
}
