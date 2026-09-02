package com.gscrm.tenant;

/**
 * {@code salon_id} taşıyan ve tenant filtresine tabi olan entity'ler.
 *
 * <p>Bu arayüzü uygulayan her entity, Hibernate tenant filtresiyle otomatik olarak
 * mevcut salona kısıtlanır. Filtre yalnızca sorgulara uygulanır; birincil anahtarla
 * yapılan {@code findById} çağrılarını Hibernate filtrelemez, onları
 * {@code TenantEntityListener} yakalar.
 */
public interface TenantEntity {

    Long getSalonId();

    /**
     * Kayıt kalıcılaştırılmadan önce tenant atanabilsin diye gereklidir; bkz.
     * {@code TenantEntityListener.onPrePersist}.
     */
    void setSalonId(Long salonId);

    /**
     * Kaydın, sahibi olmayan bir salon bağlamından okunabilmesi meşru mu?
     *
     * <p>Varsayılan {@code false}: kayıt yalnızca kendi salonunda görülebilir.
     * Organizasyon geneli veya platform geneli geçerliliği olan kayıtlar (ör.
     * ORG/GLOBAL kapsamlı kuponlar) bunu geçersiz kılar; kapsam doğrulaması o
     * durumda ilgili servisin sorumluluğundadır.
     */
    default boolean isCrossSalonReadable() {
        return false;
    }
}
