/**
 * Tenant filtresinin tanımı.
 *
 * <p>{@code salon_id} taşıyan entity'ler {@code @Filter(name = TENANT_FILTER)} ile
 * işaretlenir; filtre {@code TenantAwareJpaTransactionManager} tarafından her
 * transaction başlangıcında etkinleştirilir. Kupon gibi organizasyon geneli
 * geçerliliği olan entity'ler kendi koşullarında {@code orgId} parametresini de
 * kullanır — bu yüzden filtre iki parametreyle tanımlıdır.
 */
@FilterDef(
        name = "tenantFilter",
        parameters = {
                @ParamDef(name = "salonId", type = Long.class),
                @ParamDef(name = "orgId", type = Long.class)
        })
package com.gscrm.model;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
