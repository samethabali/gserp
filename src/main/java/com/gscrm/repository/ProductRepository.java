package com.gscrm.repository;

import com.gscrm.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByActiveTrue();
    List<Product> findByActiveTrueAndStockQuantityLessThanEqual(int threshold);

    /**
     * Aktif ürünlerden stok miktarı kendi eşik değerinin altında olanları döner.
     * ProductService.getLowStock() içindeki in-memory filtrelemeyi ortadan kaldırır.
     */
    @Query("SELECT p FROM Product p WHERE p.active = true AND p.stockQuantity <= p.lowStockThreshold")
    List<Product> findLowStock();
}
