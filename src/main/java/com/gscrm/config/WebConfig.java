package com.gscrm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.*;

import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Statik dosyaların tarayıcıda kalma süresi.
     *
     * <p>Yedi gün, "makul ölçüde uzun ama kurtarılabilir" aralığında bilinçli bir
     * seçim. {@code immutable} kullanılmıyor: sürümleme bir gün hatalı çalışırsa
     * kullanıcının sayfayı yenilemesi yeterli olmalı, tarayıcı önbelleğini elle
     * temizlemesi gerekmemeli.
     */
    private static final Duration STATIC_ASSET_TTL = Duration.ofDays(7);

    @Value("${app.cors.allowed-origins:http://localhost:8989}")
    private String allowedOrigins;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split("\\s*,\\s*");
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * CSS ve JS'in önbelleklenmesi.
     *
     * <p>Üretimde bu yollar için hiç {@code Cache-Control} tanımlanmamıştı ve
     * boşluğu Spring Security'nin varsayılanı dolduruyordu:
     * {@code no-cache, no-store, max-age=0, must-revalidate}. Sonuç, her sayfa
     * geçişinde <b>bütün</b> CSS/JS dosyalarının yeniden indirilmesiydi —
     * koşullu istek bile değil, tam indirme. Mobil bağlantıda her tıklamada
     * hissedilen bir gecikme demek.
     *
     * <p>Önbellek açılabilmesinin şartı, adresin sürüm taşıması. Şablonlar
     * {@code @{/js/x.js(v=${...asset-version})}} biçimini kullanıyor ve sürüm
     * her derlemede değişiyor (bkz. {@code pom.xml -> build.timestamp}); yani
     * yeni bir deploy adresi otomatik olarak tazeliyor.
     *
     * <p>Geliştirmede tersi isteniyor: dosya her kaydedildiğinde tarayıcının
     * yeni hâli görmesi gerekir, sürüm numarası aynı kaldığı için önbellek
     * kapalı tutulur.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        boolean development = activeProfile.contains("dev");
        registry.addResourceHandler("/js/**", "/css/**")
                .addResourceLocations("classpath:/static/js/", "classpath:/static/css/")
                .setCacheControl(development
                        ? CacheControl.noStore().mustRevalidate()
                        : CacheControl.maxAge(STATIC_ASSET_TTL).cachePublic());
    }
}
