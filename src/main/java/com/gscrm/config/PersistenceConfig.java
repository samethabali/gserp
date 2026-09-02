package com.gscrm.config;

import com.gscrm.tenant.TenantAwareJpaTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Boot'un varsayılan {@code JpaTransactionManager}'ını, tenant filtresini
 * kuran türeviyle değiştirir.
 */
@Configuration
public class PersistenceConfig {

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new TenantAwareJpaTransactionManager(emf);
    }
}
