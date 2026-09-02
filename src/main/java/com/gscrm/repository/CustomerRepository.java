package com.gscrm.repository;

import com.gscrm.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndSalonId(Long id, Long salonId);

    List<Customer> findBySalonId(Long salonId);

    Optional<Customer> findBySalonIdAndPhone(Long salonId, String phone);

    Optional<Customer> findBySalonIdAndEmail(Long salonId, String email);

    boolean existsBySalonIdAndEmail(Long salonId, String email);

    @Query("""
           SELECT c FROM Customer c
           WHERE c.salonId = :salonId
             AND (LOWER(CONCAT(c.firstName, ' ', COALESCE(c.lastName, ''))) LIKE LOWER(CONCAT('%', :query, '%'))
              OR (c.phone IS NOT NULL AND c.phone LIKE CONCAT('%', :query, '%'))
              OR (c.email IS NOT NULL AND LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%'))))
           """)
    List<Customer> searchBySalonIdAndQuery(@Param("salonId") Long salonId, @Param("query") String query);
}
