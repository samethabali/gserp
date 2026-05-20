package com.gserp.repository;

import com.gserp.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPhone(String phone);

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * DB seviyesinde müşteri arama — isim, telefon veya e-posta ile ILIKE.
     * CustomerService.getAll() içindeki in-memory filtrelemeyi ortadan kaldırır.
     */
    @Query("""
           SELECT c FROM Customer c
           WHERE LOWER(CONCAT(c.firstName, ' ', COALESCE(c.lastName, ''))) LIKE LOWER(CONCAT('%', :query, '%'))
              OR (c.phone IS NOT NULL AND c.phone LIKE CONCAT('%', :query, '%'))
              OR (c.email IS NOT NULL AND LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))
           """)
    List<Customer> searchByQuery(@Param("query") String query);
}
