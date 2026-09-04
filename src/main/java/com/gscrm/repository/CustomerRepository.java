package com.gscrm.repository;

import com.gscrm.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndSalonId(Long id, Long salonId);

    List<Customer> findBySalonId(Long salonId);

    Optional<Customer> findBySalonIdAndPhone(Long salonId, String phone);

    Optional<Customer> findBySalonIdAndEmail(Long salonId, String email);

    boolean existsBySalonIdAndEmail(Long salonId, String email);

    /**
     * Normalize telefonla eşleşen müşteriler — kararlı sıralamayla.
     *
     * <p>Aynı normalize telefonu paylaşan birden çok satır olabilir (yinelenenler
     * bilinçli olarak birleştirilmiyor). Sıralamanın kararlı olması şart: aynı
     * ziyaretçi her seferinde aynı kayda düşmeli, tanıma titrememeli. "En son
     * aktivite" ölçüt olamazdı — randevunun müşteriye FK'si yok, tüm yinelenen
     * satırlar özdeş sonuç verirdi.
     */
    @Query("""
           select c from Customer c
           where c.salonId = :salonId and c.phoneNormalized = :phoneNormalized
           order by c.consentAt desc nulls last, c.updatedAt desc nulls last, c.id asc
           """)
    List<Customer> findBySalonIdAndPhoneNormalized(@Param("salonId") Long salonId,
                                                   @Param("phoneNormalized") String phoneNormalized);

    /** Panelde "olası yinelenen müşteri" uyarısını besleyen sorgu. */
    @Query("""
           select c.phoneNormalized from Customer c
           where c.salonId = :salonId and c.phoneNormalized is not null
           group by c.phoneNormalized having count(c) > 1
           """)
    List<String> findDuplicateNormalizedPhones(@Param("salonId") Long salonId);

    @Query("""
           SELECT c FROM Customer c
           WHERE c.salonId = :salonId
             AND (LOWER(CONCAT(c.firstName, ' ', COALESCE(c.lastName, ''))) LIKE LOWER(CONCAT('%', :query, '%'))
              OR (c.phone IS NOT NULL AND c.phone LIKE CONCAT('%', :query, '%'))
              OR (c.email IS NOT NULL AND LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%'))))
           """)
    List<Customer> searchBySalonIdAndQuery(@Param("salonId") Long salonId, @Param("query") String query);
}
