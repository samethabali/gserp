package com.gscrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.tenant")
public class TenantProperties {

    /** Apex domain (örn. gscrm.avesitesi.xyz) — subdomain tenant çözümlemesi için */
    private String baseDomain = "";

    private String salonSlugCookie = "gscrm-salon-slug";
}
