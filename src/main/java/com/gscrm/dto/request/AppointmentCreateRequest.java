package com.gscrm.dto.request;

import com.gscrm.model.enums.BodyRegion;
import com.gscrm.validation.PhoneNumber;

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
    @PhoneNumber
    private String customerPhone;

    /**
     * Bot tuzağı: ekranda görünmeyen alan. İnsan doldurmaz, formu otomatik dolduran
     * betikler doldurur. Bilerek <b>doğrulama anotasyonu yok</b> — {@code @Null}
     * koysaydık dönen hata mesajı bota hangi alanın tuzak olduğunu söylerdi.
     */
    private String website;

    /** Form açıldıktan sonra geçen süre (ms); anında gönderim bot işaretidir. */
    private Long elapsedMs;

    /**
     * Telefon doğrulamasından dönen tek kullanımlık kulp.
     *
     * <p>Gövdede taşınıyor, header/Bearer olarak değil: JWT filtresine hiç değmemeli.
     * Doğrulama bayrağı kapalıyken yok sayılır.
     */
    private String verificationToken;

    @NotNull(message = "Uzman seçilmelidir")
    private Long staffId;

    @NotNull(message = "Hizmet seçilmelidir")
    private Long serviceId;

    @NotNull(message = "Başlangıç saati girilmelidir")
    private LocalDateTime startTime;

    /**
     * Epilasyon randevusunda insan vücudu şablonundan seçilen bölgeler.
     *
     * <p>Tip enum: geçersiz bir kod istekten hiç içeri giremez. Tavan, tek istekte
     * katalogdaki bölge sayısından fazlasını göndermeyi anlamsız kıldığı için var.
     */
    @Size(max = 30, message = "En fazla 30 vücut bölgesi seçilebilir")
    private List<BodyRegion> bodyRegions;

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
