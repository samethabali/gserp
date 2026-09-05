package com.gscrm.repository;

import com.gscrm.model.User;
import com.gscrm.model.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findBySalonIdAndUsername(Long salonId, String username);

    boolean existsBySalonIdAndUsername(Long salonId, String username);

    Optional<User> findByUsername(String username);

    /**
     * Personel kullanıcısını adıyla bulur (müşteri portalı hesapları hariç).
     *
     * <p>Müşteri hesapları e-posta ile kayıt olduğu için salon bazlı tekildir; onları
     * dışarıda bırakmadan sistem geneli arama, iki işletmede aynı e-postayla kayıtlı
     * müşterilerde belirsiz sonuç verirdi.
     */
    @Query("SELECT u FROM User u WHERE u.username = :username AND u.role <> com.gscrm.model.enums.UserRole.CUSTOMER")
    Optional<User> findStaffByUsername(@Param("username") String username);

    @Query("SELECT COUNT(u) FROM User u WHERE u.username = :username AND u.role <> com.gscrm.model.enums.UserRole.CUSTOMER")
    long countStaffByUsername(@Param("username") String username);

    Optional<User> findByCustomerId(Long customerId);

    List<User> findBySalonId(Long salonId);

    long countByOrganizationIdAndEnabledTrue(Long organizationId);

    Optional<User> findByIdAndSalonId(Long id, Long salonId);

    /** Personel kaydına bağlı giriş hesabı; personel başına en fazla bir tane vardır. */
    Optional<User> findBySalonIdAndStaffId(Long salonId, Long staffId);

    List<User> findBySalonIdAndStaffIdNotNull(Long salonId);

    /**
     * Org'a ait, seat kotasına dahil kullanıcı sayısı (CUSTOMER ve PLATFORM_ADMIN hariç).
     * Kota sayımı için DB tarafında hesaplanır (findAll bellek taraması yerine).
     */
    @Query("select count(u) from User u where u.organizationId = :orgId "
            + "and u.role not in :excludedRoles")
    long countSeatUsersByOrganization(@Param("orgId") Long organizationId,
                                      @Param("excludedRoles") List<UserRole> excludedRoles);

    /**
     * Platform panelindeki kiracı listesi için salon başına kullanıcı sayısı.
     *
     * <p>Tek sorgu: liste her satır için ayrı sayım yapsaydı kiracı sayısı kadar
     * sorgu çalışırdı.
     */
    @Query("SELECT u.salonId, COUNT(u) FROM User u WHERE u.salonId IN :salonIds GROUP BY u.salonId")
    java.util.List<Object[]> countGroupedBySalonIds(@Param("salonIds") java.util.Collection<Long> salonIds);

    /**
     * Salon başına, "hesabına gir" için hedeflenecek yönetici kullanıcı.
     *
     * <p>Panel bunu bilmeden impersonation yapamıyordu; kullanıcı id'sini elle
     * sormak yerine en düşük id'li yönetici seçilir.
     */
    @Query("""
            SELECT u.salonId, MIN(u.id) FROM User u
            WHERE u.salonId IN :salonIds
              AND u.role IN (com.gscrm.model.enums.UserRole.ADMIN,
                             com.gscrm.model.enums.UserRole.BRANCH_MANAGER,
                             com.gscrm.model.enums.UserRole.ORG_OWNER)
              AND u.enabled = true
            GROUP BY u.salonId
            """)
    java.util.List<Object[]> findAdminUserIdsBySalonIds(@Param("salonIds") java.util.Collection<Long> salonIds);
}
