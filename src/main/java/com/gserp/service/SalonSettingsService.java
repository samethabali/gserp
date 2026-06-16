package com.gserp.service;

import com.gserp.model.SalonSetting;
import com.gserp.repository.SalonSettingRepository;
import com.gserp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SalonSettingsService {

    private final SalonSettingRepository repository;

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        Long salonId = TenantContext.requireSalonId();
        return repository.findBySalonIdAndKey(salonId, key).map(SalonSetting::getValue).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getPublicSettings() {
        Map<String, String> map = new HashMap<>();
        map.put("name", get("salon.name", "GSERP Salon"));
        map.put("logoUrl", get("salon.logo_url", ""));
        map.put("primaryColor", get("salon.primary_color", "#e91e8c"));
        return map;
    }

    @Transactional
    public void set(String key, String value) {
        Long salonId = TenantContext.requireSalonId();
        SalonSetting setting = repository.findBySalonIdAndKey(salonId, key)
                .orElse(SalonSetting.builder().salonId(salonId).key(key).build());
        setting.setSalonId(salonId);
        setting.setValue(value);
        setting.setUpdatedAt(LocalDateTime.now());
        repository.save(setting);
    }
}
