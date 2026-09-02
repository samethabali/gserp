package com.gscrm.dto.request;

import com.gscrm.model.enums.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseCreateRequest {

    @NotBlank(message = "Açıklama girilmelidir")
    @Size(max = 255)
    private String description;

    @NotNull(message = "Tutar girilmelidir")
    @DecimalMin(value = "0.01", message = "Tutar sıfırdan büyük olmalı")
    private BigDecimal amount;

    @NotNull(message = "Tarih girilmelidir")
    private LocalDate expenseDate;

    @NotNull(message = "Kategori girilmelidir")
    private ExpenseCategory category;
}
