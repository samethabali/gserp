package com.gscrm.repository;

import com.gscrm.model.ServiceDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceDefinitionRepository extends JpaRepository<ServiceDefinition, Long> {

    Optional<ServiceDefinition> findByIdAndSalonId(Long id, Long salonId);

    List<ServiceDefinition> findBySalonIdAndActiveTrue(Long salonId);

    /**
     * Salonun tüm hizmetleri — pasifler dahil.
     *
     * <p>Randevu listelerini toplu dönüştürürken gerekiyor: geçmiş bir randevu
     * sonradan pasife alınmış bir hizmete işaret edebilir ve yalnızca aktifler
     * yüklenirse o randevunun hizmet adı "?" görünür.
     */
    List<ServiceDefinition> findBySalonId(Long salonId);

    boolean existsBySalonId(Long salonId);
}
