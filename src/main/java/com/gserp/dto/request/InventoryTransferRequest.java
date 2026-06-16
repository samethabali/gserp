package com.gserp.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class InventoryTransferRequest {

    @NotNull
    private Long fromSalonId;

    @NotNull
    private Long toSalonId;

    @NotNull
    private Long productId;

    @Positive
    private int quantity;
}
