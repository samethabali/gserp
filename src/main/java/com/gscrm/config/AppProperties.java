package com.gscrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * Uygulamanın dışarıdan görünen adresi (ör. https://gscrm.avesitesi.xyz).
     *
     * <p>Kiracı çözümlemesinde <b>kullanılmaz</b>; yalnızca paylaşılabilir bağlantı
     * üretir (davet linki, işletmenin randevu linki). Eskiden aynı değer alt alan
     * adından kiracı çözmek için kullanılıyordu ve canlı domain ile eşleşmediğinde
     * her istek 404 dönüyordu.
     */
    private String publicBaseUrl = "";

    /** Davet kodu taşımayan kayıtlar için varsayılan deneme süresi (gün). */
    private int defaultTrialDays = 14;
}
