package com.gscrm.repository;

import com.gscrm.model.InviteRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InviteRedemptionRepository extends JpaRepository<InviteRedemption, Long> {

    List<InviteRedemption> findByInviteCodeIdInOrderByRedeemedAtDesc(Collection<Long> inviteCodeIds);

    Optional<InviteRedemption> findByOrganizationId(Long organizationId);

    List<InviteRedemption> findByOrganizationIdIn(Collection<Long> organizationIds);
}
