package com.gscrm.repository;

import com.gscrm.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findBySalonIdAndUsername(Long salonId, String username);

    boolean existsBySalonIdAndUsername(Long salonId, String username);

    Optional<User> findByUsername(String username);

    Optional<User> findByCustomerId(Long customerId);

    List<User> findBySalonId(Long salonId);

    Optional<User> findByIdAndSalonId(Long id, Long salonId);
}
