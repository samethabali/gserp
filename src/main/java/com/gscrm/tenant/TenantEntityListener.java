package com.gscrm.tenant;

import org.springframework.security.access.AccessDeniedException;

/**
 * Birincil anahtarla yüklenen kayıtların tenant sahipliğini doğrular.
 *
 * <p>Hibernate filtreleri yalnızca sorgulara uygulanır; {@code EntityManager.find()}
 * — yani Spring Data'nın {@code findById}'si — filtreyi <b>atlar</b>. Bu yüzden
 * kimlikle erişim (IDOR) yalnızca filtreyle kapanmaz. Bu dinleyici, yüklenen her
 * tenant kaydının mevcut salona ait olduğunu doğrular ve değilse erişimi reddeder.
 *
 * <p>{@code META-INF/orm.xml} üzerinden varsayılan dinleyici olarak kayıtlıdır, bu
 * yüzden yeni bir entity eklendiğinde ayrıca bağlanması gerekmez.
 */
public class TenantEntityListener {

    /**
     * Yeni kaydın tenant'ını doldurur.
     *
     * <p>V15 migration'ı {@code salon_id} sütunlarını {@code NOT NULL} yaptı, ancak
     * birçok yazma yolu bu alanı hiç doldurmuyordu; sonuç, kayıt oluşturmanın
     * veritabanı kısıtına takılıp 500 dönmesiydi. Alanı burada doldurmak, her yeni
     * servis için tek tek hatırlanması gereken bir adımı ortadan kaldırır.
     *
     * <p>Zaten dolu olan {@code salonId} korunur: şubeler arası stok transferi gibi
     * meşru akışlar, bilinçli olarak başka bir şubeye kayıt yazar.
     */
    public void onPrePersist(Object entity) {
        if (!(entity instanceof TenantEntity tenantEntity)) {
            return;
        }
        if (tenantEntity.getSalonId() != null) {
            return;
        }
        Long currentSalonId = TenantContext.getSalonId();
        if (currentSalonId != null) {
            tenantEntity.setSalonId(currentSalonId);
        }
    }

    public void onPostLoad(Object entity) {
        if (!(entity instanceof TenantEntity tenantEntity)) {
            return;
        }
        if (!TenantFilterSupport.shouldFilter()) {
            return;
        }
        Long entitySalonId = tenantEntity.getSalonId();
        if (entitySalonId == null) {
            return;
        }
        Long currentSalonId = TenantContext.getSalonId();
        if (entitySalonId.equals(currentSalonId)) {
            return;
        }
        if (tenantEntity.isCrossSalonReadable()) {
            return;
        }
        throw new AccessDeniedException("Bu kayda erişim yetkiniz yok");
    }
}
