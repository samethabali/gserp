package com.gscrm.repository;

import com.gscrm.model.Salon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalonRepository extends JpaRepository<Salon, Long> {

    Optional<Salon> findBySlugAndActiveTrue(String slug);

    List<Salon> findByOrganizationIdAndActiveTrue(Long organizationId);

    /**
     * Platform yöneticisinin işletme listesi.
     *
     * <p>Bu rol hiçbir organizasyona bağlı değildir; şube seçicisi ona
     * organizasyonun değil, platformdaki tüm aktif işletmelerin listesini verir.
     */
    List<Salon> findByActiveTrueOrderByIdAsc();

    /** Kiracı bağlamı olmayan platform yöneticisine benimsetilecek varsayılan işletme. */
    Optional<Salon> findFirstByActiveTrueOrderByIdAsc();

    /**
     * Slug çakışma kontrolü aktiflikten bağımsız olmalı.
     *
     * <p>Kontrol {@code findBySlugAndActiveTrue} ile yapılırken askıya alınmış bir
     * salonun slug'ı uygulama kontrolünü geçiyor, ardından DB unique kısıtında
     * patlıyordu: kullanıcı "bu slug kullanılıyor" yerine 500 alıyordu.
     */
    boolean existsBySlug(String slug);

    List<Salon> findByOrganizationId(Long organizationId);

    List<Salon> findByOrganizationIdIn(Collection<Long> organizationIds);
}
