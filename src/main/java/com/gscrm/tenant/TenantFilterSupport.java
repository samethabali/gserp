package com.gscrm.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * Tenant filtresini açan/kapatan tek nokta.
 *
 * <p>Filtre normalde {@link TenantAwareJpaTransactionManager} tarafından transaction
 * başlangıcında etkinleştirilir. Bu sınıf, halihazırda açık bir transaction içinde
 * de filtreyi geçici olarak kapatabilmek için kullanılır — org geneli raporlar gibi
 * dar yollar için.
 */
@Slf4j
@Component
public class TenantFilterSupport {

    public static final String FILTER_NAME = "tenantFilter";
    public static final String PARAM_SALON_ID = "salonId";
    public static final String PARAM_ORG_ID = "orgId";

    /** orgId tanımsızken filtreye geçilen, hiçbir kaydın eşleşmeyeceği değer. */
    private static final long NO_ORG = -1L;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Verilen işi tenant filtresi kapalıyken çalıştırır. Açık bir transaction varsa
     * filtre o session üzerinde geçici olarak devre dışı bırakılır ve iş bitince
     * yeniden kurulur.
     */
    public <T> T runUnfiltered(Supplier<T> action) {
        boolean sessionActive = TransactionSynchronizationManager.isActualTransactionActive();
        if (sessionActive) {
            disableOnCurrentSession();
        }
        try {
            return TenantFilterContext.runUnfiltered(action);
        } finally {
            if (sessionActive) {
                enableOnCurrentSession();
            }
        }
    }

    public void runUnfiltered(Runnable action) {
        runUnfiltered(() -> {
            action.run();
            return null;
        });
    }

    private void disableOnCurrentSession() {
        try {
            entityManager.unwrap(Session.class).disableFilter(FILTER_NAME);
        } catch (RuntimeException e) {
            log.debug("Tenant filtresi kapatılamadı (muhtemelen zaten kapalı): {}", e.getMessage());
        }
    }

    private void enableOnCurrentSession() {
        Long salonId = TenantContext.getSalonId();
        if (salonId == null || TenantContext.isPlatformBypass()) {
            return;
        }
        try {
            enable(entityManager.unwrap(Session.class), salonId, TenantContext.getOrgId());
        } catch (RuntimeException e) {
            log.debug("Tenant filtresi yeniden kurulamadı: {}", e.getMessage());
        }
    }

    /** Filtreyi verilen session üzerinde etkinleştirir. */
    public static void enable(Session session, Long salonId, Long orgId) {
        session.enableFilter(FILTER_NAME)
                .setParameter(PARAM_SALON_ID, salonId)
                .setParameter(PARAM_ORG_ID, orgId != null ? orgId : NO_ORG);
    }

    /**
     * Bu istek/iş için filtre uygulanmalı mı?
     *
     * <p>Salon bağlamı yoksa filtre kurulamaz. HTTP yolunda bu yalnızca
     * {@code TenantFilter}'ın muaf tuttuğu uçlarda olur (platform, actuator,
     * kayıt) — oralarda tenant kısıtı zaten anlamsızdır. Arka plan işleri
     * bağlamı kendileri kurar veya {@code runUnfiltered} ile açıkça muaftır.
     */
    public static boolean shouldFilter() {
        return TenantContext.getSalonId() != null
                && !TenantContext.isPlatformBypass()
                && !TenantFilterContext.isUnfiltered();
    }
}
