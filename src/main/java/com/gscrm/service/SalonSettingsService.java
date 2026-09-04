package com.gscrm.service;

import com.gscrm.config.AppProperties;
import com.gscrm.model.SalonSetting;
import com.gscrm.repository.SalonSettingRepository;
import com.gscrm.tenant.TenantContext;
import com.gscrm.util.FieldDiff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalonSettingsService {

    private final SalonSettingRepository repository;
    private final ActivityEventService activityEventService;
    private final AppProperties appProperties;

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
     * <p>Link {@code app.public-base-url} + {@code /b/{slug}} olarak üretiliyor ve
     * bugüne kadar yalnızca kayıt sihirbazında bir kez görünüyordu; salon sahibi
     * o ekranı geçtikten sonra kendi randevu adresini bulabileceği bir yer yoktu.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getManagementSettings() {
        Map<String, String> map = new HashMap<>(getPublicSettings());
        map.put("bookingUrl", bookingUrl());
        return map;
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
        // Taban adres yapılandırılmamışsa göreli yol dön: panel yine de linki
        // gösterebilsin, "null/b/salon" gibi bozuk bir adres üretmeyelim.
        return base + "/b/" + slug;
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
