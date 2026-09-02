package com.gscrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentCreateRequest {

    /**
     * Bu uç herkese açıktır ({@code POST /api/booking/request}) ve buradaki değer
     * personel panelinde listelenir. Uzunluk ve karakter kısıtı, hem saçma verinin
     * hem de betik enjeksiyon denemelerinin kaynakta durdurulması içindir.
     */
    @NotBlank(message = "Müşteri adı girilmelidir")
    @Size(max = 100, message = "Müşteri adı en fazla 100 karakter olabilir")
    @Pattern(regexp = "[^<>{}\\\\]*", message = "Müşteri adı geçersiz karakter içeriyor")
    private String customerName;

    @Size(max = 20, message = "Telefon numarası en fazla 20 karakter olabilir")
    @Pattern(regexp = "|[0-9+()\\s-]{7,20}", message = "Geçerli bir telefon numarası girin")
    private String customerPhone;

    @NotNull(message = "Uzman seçilmelidir")
    private Long staffId;

    @NotNull(message = "Hizmet seçilmelidir")
    private Long serviceId;

    @NotNull(message = "Başlangıç saati girilmelidir")
    private LocalDateTime startTime;

    private BigDecimal finalPrice;
    private BigDecimal adjustment;
    private String adjustmentNote;
    private String internalNote;
    private List<FlagRequest> flags;

    // ─── Session support ───
    private Integer numberOfSessions;       // null veya 1 = tek randevu; >1 = çoklu seans
    private DayOfWeek preferredDayOfWeek;   // tercih edilen gün (seans sistemi için)

    // ─── Campaign support ───
    private String couponCode;              // müşteri portalından kupon kodu

    private java.util.List<String> consentTypes;
}
