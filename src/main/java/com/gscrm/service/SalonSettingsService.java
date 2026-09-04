package com.gscrm.service;

import com.gscrm.config.AppProperties;
import com.gscrm.model.SalonSetting;
import com.gscrm.model.Salon;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.SalonSettingRepository;
import com.gscrm.tenant.PublicBookingPath;
import com.gscrm.tenant.TenantContext;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.gscrm.util.FieldDiff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalonSettingsService {

    private final SalonSettingRepository repository;
    private final ActivityEventService activityEventService;
    private final AppProperties appProperties;
    private final SalonRepository salonRepository;

    private static final int MAX_LOGO_DATA_URL_LENGTH = 700_000;

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        Long salonId = TenantContext.requireSalonId();
        return repository.findBySalonIdAndKey(salonId, key).map(SalonSetting::getValue).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getPublicSettings() {
        Map<String, String> map = new HashMap<>();
        map.put("name", get("salon.name", "GSCRM Salon"));
        map.put("logoUrl", get("salon.logo_url", ""));
        map.put("primaryColor", get("salon.primary_color", "#e91e8c"));
        map.put("showcase", TenantContext.isShowcase() ? "true" : "false");
        // Booking sayfası doğrulama adımını gösterip göstermeyeceğini buradan öğrenir.
        // Showcase salonlarda zorla kapalı: demo tenant gerçek gönderim denemesi yapmasın.
        map.put("smsVerificationEnabled",
                !TenantContext.isShowcase() && Boolean.parseBoolean(get("booking.sms_verification_enabled", "false"))
                        ? "true" : "false");
        return map;
    }

    /**
     * Panelde gösterilen ayarlar: herkese açık alanlar + işletmenin randevu linki.
     *
     * <p>Link {@code app.public-base-url} + {@code /{slug}} olarak üretiliyor ve
     * bugüne kadar yalnızca kayıt sihirbazında bir kez görünüyordu; salon sahibi
     * o ekranı geçtikten sonra kendi randevu adresini bulabileceği bir yer yoktu.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getManagementSettings() {
        Map<String, String> map = new HashMap<>(getPublicSettings());
        map.put("bookingUrl", bookingUrl());
        return map;
    }

    /** Salon adını ve bu addan üretilen herkese açık adresi birlikte günceller. */
    @Transactional
    public void updateSalonName(String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.length() < 2 || normalizedName.length() > 255) {
            throw new IllegalArgumentException("Salon adı 2-255 karakter olmalı");
        }

        Long salonId = TenantContext.requireSalonId();
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new IllegalArgumentException("Salon bulunamadı"));
        String newSlug = slugify(normalizedName);
        if (newSlug.length() < 2 || PublicBookingPath.isReserved(newSlug)) {
            throw new IllegalArgumentException("Salon adından geçerli bir randevu adresi oluşturulamadı");
        }
        if (!newSlug.equals(salon.getSlug()) && salonRepository.existsBySlug(newSlug)) {
            throw new IllegalArgumentException("Bu salon adına ait randevu adresi zaten kullanılıyor");
        }

        salon.setName(normalizedName);
        salon.setSlug(newSlug);
        salonRepository.save(salon);
        set("salon.name", normalizedName);
    }

    /** Yalnızca küçük PNG/JPEG/WebP logo veri adreslerini kabul eder. */
    @Transactional
    public void updateLogo(String logoDataUrl) {
        String value = logoDataUrl == null ? "" : logoDataUrl.trim();
        if (!value.isEmpty()) {
            if (value.length() > MAX_LOGO_DATA_URL_LENGTH
                    || !value.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=\\r\\n]+$")) {
                throw new IllegalArgumentException("Logo PNG, JPEG veya WebP formatında ve en fazla 512 KB olmalı");
            }
        }
        set("salon.logo_url", value);
    }

    private String slugify(String value) {
        String ascii = value.toLowerCase(java.util.Locale.forLanguageTag("tr"))
                .replace('ı', 'i').replace('ğ', 'g').replace('ş', 's')
                .replace('ç', 'c').replace('ö', 'o').replace('ü', 'u');
        ascii = Normalizer.normalize(ascii, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return ascii.length() > 63 ? ascii.substring(0, 63).replaceAll("-+$", "") : ascii;
    }

    private String bookingUrl() {
        String slug = TenantContext.getSlug();
        if (slug == null || slug.isBlank()) {
            return "";
        }
        String base = appProperties.getPublicBaseUrl();
        base = base == null ? "" : base.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        // Yapılandırılmamışsa adresi isteğin kendisinden türet. Sabit bir varsayılan
        // (eskiden http://localhost:8989) salon sahibine paylaşamayacağı bir link
        // gösteriyordu; istek zaten doğru genel adresi taşıyor.
        if (base.isEmpty()) {
            base = currentOrigin();
        }
        return base + "/" + slug;
    }

    /** Vekil başlıkları {@code forward-headers-strategy: framework} ile çözülmüş genel adres. */
    private String currentOrigin() {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (IllegalStateException e) {
            // HTTP isteği dışında (zamanlanmış iş, test) çağrıldıysa göreli yol kalsın.
            return "";
        }
    }

    @Transactional
    public void set(String key, String value) {
        Long salonId = TenantContext.requireSalonId();
        SalonSetting setting = repository.findBySalonIdAndKey(salonId, key)
                .orElse(SalonSetting.builder().salonId(salonId).key(key).build());
        String previous = setting.getValue();
        setting.setSalonId(salonId);
        setting.setValue(value);
        setting.setUpdatedAt(LocalDateTime.now());
        repository.save(setting);

        // Ayar değişiklikleri iz bırakmıyordu: çalışma saati ya da doğrulama anahtarı
        // değiştiğinde kimin ne zaman değiştirdiği hiçbir yerde görünmüyordu.
        activityEventService.recordChange("SETTING_CHANGE", "SALON_SETTING", setting.getId(), null,
                "Ayar değişti: " + key,
                FieldDiff.create().compare(key, previous, value).toJson());
    }
}
