package com.gscrm.repository;

import com.gscrm.model.OnboardingState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingStateRepository extends JpaRepository<OnboardingState, Long> {
    Optional<OnboardingState> findBySalonId(Long salonId);
}
