package com.gserp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (activeProfile.contains("dev")) {
            registry.addResourceHandler("/js/**", "/css/**")
                    .addResourceLocations("classpath:/static/js/", "classpath:/static/css/")
                    .setCacheControl(CacheControl.noStore().mustRevalidate());
        }
    }
}
