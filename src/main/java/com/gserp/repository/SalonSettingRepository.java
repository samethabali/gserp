package com.gserp.repository;

import com.gserp.model.SalonSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalonSettingRepository extends JpaRepository<SalonSetting, Long> {

    Optional<SalonSetting> findBySalonIdAndKey(Long salonId, String key);

    List<SalonSetting> findBySalonId(Long salonId);
}
