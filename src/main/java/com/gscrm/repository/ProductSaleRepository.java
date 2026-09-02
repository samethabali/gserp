package com.gscrm.repository;

import com.gscrm.model.ProductSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductSaleRepository extends JpaRepository<ProductSale, Long> {
    List<ProductSale> findBySoldAtBetweenOrderBySoldAtDesc(LocalDateTime from, LocalDateTime to);
    List<ProductSale> findByCustomerIdOrderBySoldAtDesc(Long customerId);
}
