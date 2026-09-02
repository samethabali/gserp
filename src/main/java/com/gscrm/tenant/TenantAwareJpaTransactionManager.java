package com.gscrm.tenant;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Her transaction başlangıcında Hibernate tenant filtresini kurar.
 *
 * <p>Bu, izolasyonun <b>varsayılan açık</b> olmasını sağlayan yerdir: sorguyu yazan
 * kişi hiçbir şey yapmasa bile, tenant entity'lerine giden her sorguya
 * {@code salon_id = :salonId} koşulu eklenir. Muafiyet için
 * {@link TenantFilterSupport#runUnfiltered} kullanılır.
 */
@Slf4j
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    public TenantAwareJpaTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        if (!TenantFilterSupport.shouldFilter()) {
            return;
        }
        EntityManagerFactory emf = getEntityManagerFactory();
        if (emf == null) {
            return;
        }
        Object resource = TransactionSynchronizationManager.getResource(emf);
        if (!(resource instanceof EntityManagerHolder holder)) {
            return;
        }
        try {
            Session session = holder.getEntityManager().unwrap(Session.class);
            TenantFilterSupport.enable(session, TenantContext.getSalonId(), TenantContext.getOrgId());
        } catch (RuntimeException e) {
            // Filtre kurulamıyorsa transaction'ı açık bırakmak sızıntı riski demektir.
            throw new IllegalStateException("Tenant filtresi kurulamadı; istek reddedildi", e);
        }
    }
}
