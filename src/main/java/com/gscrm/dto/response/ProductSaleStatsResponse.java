package com.gscrm.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSaleStatsResponse {
    private int totalSalesCount;
    private BigDecimal totalRevenue;
    private BigDecimal totalProfit;

    private List<ProductStats> topByQuantity;
    private List<ProductStats> topByRevenue;
    private List<ProductStats> topByProfit;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductStats {
        private Long productId;
        private String productName;
        private String category;
        private int quantitySold;
        private BigDecimal totalRevenue;
        private BigDecimal totalProfit;
    }
}
