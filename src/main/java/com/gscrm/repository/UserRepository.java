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

    Optional<User> findByCustomerId(Long customerId);

    List<User> findBySalonId(Long salonId);

    long countByOrganizationIdAndEnabledTrue(Long organizationId);

    Optional<User> findByIdAndSalonId(Long id, Long salonId);

    /**
     * Org'a ait, seat kotasına dahil kullanıcı sayısı (CUSTOMER ve PLATFORM_ADMIN hariç).
     * Kota sayımı için DB tarafında hesaplanır (findAll bellek taraması yerine).
     */
    @Query("select count(u) from User u where u.organizationId = :orgId "
            + "and u.role not in :excludedRoles")
    long countSeatUsersByOrganization(@Param("orgId") Long organizationId,
                                      @Param("excludedRoles") List<UserRole> excludedRoles);
}
