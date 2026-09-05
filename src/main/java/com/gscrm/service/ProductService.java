package com.gscrm.service;

import com.gscrm.model.Product;
import com.gscrm.model.ProductSale;
import com.gscrm.repository.ProductRepository;
import com.gscrm.repository.ProductSaleRepository;
import com.gscrm.model.Customer;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.dto.response.ProductSaleStatsResponse;
import com.gscrm.tenant.TenantContext;
import com.gscrm.util.FieldDiff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CustomerMatchingService customerMatchingService;
    private final ProductSaleRepository productSaleRepository;
    private final CustomerRepository customerRepository;
    private final ActivityEventService activityEventService;

    public List<Product> getAll() { return productRepository.findAll(); }
    public List<Product> getActive() { return productRepository.findByActiveTrue(); }
    public Optional<Product> getById(Long id) { return productRepository.findById(id); }

    public List<Product> getLowStock() {
        return productRepository.findLowStock();
    }

    @Transactional
    public Product create(Product product) {
        // Uçlar ham entity kabul ettiği için istemci gövdeye salonId koyabilir;
        // tenant sunucu tarafında zorlanır (mass assignment koruması).
        product.setSalonId(TenantContext.requireSalonId());
        Product saved = productRepository.save(product);
        activityEventService.record("CREATE", "PRODUCT", saved.getId(), null,
                "Ürün eklendi: " + saved.getName());
        return saved;
    }

    @Transactional
    public Product update(Long id, Product updated) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı: " + id));

        // Setter'lardan önce okunmalı: sonrasında eski değer artık okunamaz.
        String prevName = p.getName();
        var prevPrice = p.getPrice();
        var prevCost = p.getCostPrice();
        Integer prevThreshold = p.getLowStockThreshold();
        boolean prevActive = p.isActive();

        p.setName(updated.getName());
        p.setCategory(updated.getCategory());
        p.setPrice(updated.getPrice());
        p.setCostPrice(updated.getCostPrice());
        p.setLowStockThreshold(updated.getLowStockThreshold());
        p.setActive(updated.isActive());
        Product saved = productRepository.save(p);
        activityEventService.recordChange("UPDATE", "PRODUCT", saved.getId(), null,
                "Ürün güncellendi: " + saved.getName(),
                FieldDiff.create()
                        .compare("ad", prevName, saved.getName())
                        .compare("fiyat", prevPrice, saved.getPrice())
                        .compare("maliyet", prevCost, saved.getCostPrice())
                        .compare("kritikStok", prevThreshold, saved.getLowStockThreshold())
                        .compare("aktif", prevActive, saved.isActive())
                        .toJson());
        return saved;
    }

    @Transactional
    public ProductSale sell(Long productId, int quantity, Long appointmentId, Long customerId,
                            String customerPhone, String customerName, Long staffId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı: " + productId));
        if (product.getStockQuantity() < quantity) {
            throw new IllegalArgumentException("Yetersiz stok. Mevcut: " + product.getStockQuantity());
        }
        product.setStockQuantity(product.getStockQuantity() - quantity);
        productRepository.save(product);

        // Find customer if not directly provided but phone exists
        Long resolvedCustomerId = customerId;
        if (resolvedCustomerId == null && customerPhone != null && !customerPhone.isBlank()) {
            resolvedCustomerId = customerMatchingService.findByPhone(customerPhone)
                    .map(Customer::getId)
                    .orElse(null);
        }

        BigDecimal total = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        ProductSale sale = ProductSale.builder()
                .productId(productId)
                .quantity(quantity)
                .unitPrice(product.getPrice())
                .totalPrice(total)
                .appointmentId(appointmentId)
                .customerId(resolvedCustomerId)
                .customerPhone(customerPhone)
                .customerName(customerName)
                .staffId(staffId)
                .build();
        ProductSale saved = productSaleRepository.save(sale);
        activityEventService.record("PAYMENT", "PRODUCT_SALE", saved.getId(), resolvedCustomerId,
                "Ürün satışı: " + product.getName() + " x" + quantity);
        return saved;
    }

    @Transactional
    public Product restock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı: " + productId));
        product.setStockQuantity(product.getStockQuantity() + quantity);
        return productRepository.save(product);
    }

    public List<ProductSale> getSalesByDate(LocalDate date) {
        return productSaleRepository.findBySoldAtBetweenOrderBySoldAtDesc(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    public List<ProductSale> getSalesByDateRange(LocalDate start, LocalDate end) {
        return productSaleRepository.findBySoldAtBetweenOrderBySoldAtDesc(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    public ProductSaleStatsResponse getSalesStats(LocalDate start, LocalDate end) {
        List<ProductSale> sales = productSaleRepository.findBySoldAtBetweenOrderBySoldAtDesc(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        int totalCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;

        Map<Long, List<ProductSale>> salesByProduct = sales.stream()
                .collect(Collectors.groupingBy(ProductSale::getProductId));

        List<ProductSaleStatsResponse.ProductStats> statsList = new java.util.ArrayList<>();

        for (var entry : salesByProduct.entrySet()) {
            Long prdId = entry.getKey();
            Product p = products.get(prdId);
            if (p == null) continue;

            List<ProductSale> pSales = entry.getValue();
            int qty = pSales.stream().mapToInt(ProductSale::getQuantity).sum();
            BigDecimal rev = pSales.stream()
                    .map(ProductSale::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal cost = p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO;
            BigDecimal totalCost = cost.multiply(BigDecimal.valueOf(qty));
            BigDecimal profit = rev.subtract(totalCost);

            totalCount += qty;
            totalRevenue = totalRevenue.add(rev);
            totalProfit = totalProfit.add(profit);

            statsList.add(ProductSaleStatsResponse.ProductStats.builder()
                    .productId(prdId)
                    .productName(p.getName())
                    .category(p.getCategory())
                    .quantitySold(qty)
                    .totalRevenue(rev)
                    .totalProfit(profit)
                    .build());
        }

        List<ProductSaleStatsResponse.ProductStats> topQty = statsList.stream()
                .sorted((a, b) -> Integer.compare(b.getQuantitySold(), a.getQuantitySold()))
                .limit(5)
                .collect(Collectors.toList());

        List<ProductSaleStatsResponse.ProductStats> topRev = statsList.stream()
                .sorted((a, b) -> b.getTotalRevenue().compareTo(a.getTotalRevenue()))
                .limit(5)
                .collect(Collectors.toList());

        List<ProductSaleStatsResponse.ProductStats> topProf = statsList.stream()
                .sorted((a, b) -> b.getTotalProfit().compareTo(a.getTotalProfit()))
                .limit(5)
                .collect(Collectors.toList());

        return ProductSaleStatsResponse.builder()
                .totalSalesCount(totalCount)
                .totalRevenue(totalRevenue)
                .totalProfit(totalProfit)
                .topByQuantity(topQty)
                .topByRevenue(topRev)
                .topByProfit(topProf)
                .build();
    }
}
