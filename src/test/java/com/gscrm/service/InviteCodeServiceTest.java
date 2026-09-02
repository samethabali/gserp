package com.gscrm.service;

import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.InviteCode;
import com.gscrm.model.enums.InviteKind;
import com.gscrm.repository.InviteCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteCodeServiceTest {

    @Mock
    private InviteCodeRepository inviteCodeRepository;
    @Mock
    private SalonProvisioningService salonProvisioningService;

    @InjectMocks
    private InviteCodeService inviteCodeService;

    @Test
    void registerWithoutCodeDenied() {
        TenantProvisionRequest request = new TenantProvisionRequest();
        assertThatThrownBy(() -> inviteCodeService.registerWithInvite(request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void registerWithUnknownCodeDenied() {
        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setInviteCode("GSCRM-XXXX-YYYY");
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-XXXX-YYYY")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> inviteCodeService.registerWithInvite(request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void registerConsumesValidCode() {
        InviteCode invite = InviteCode.builder()
                .id(1L)
                .code("GSCRM-ABCD-EFGH")
                .kind(InviteKind.PILOT)
                .maxUses(1)
                .usedCount(0)
                .planCode("SOLO")
                .createdAt(LocalDateTime.now())
                .build();
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-ABCD-EFGH")).thenReturn(Optional.of(invite));
        when(salonProvisioningService.provision(any())).thenReturn(TenantProvisionResponse.builder()
                .organizationId(9L)
                .salonId(3L)
                .salonSlug("yeni")
                .build());

        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setInviteCode("gscrm-abcd-efgh");
        request.setOrganizationName("Org");
        request.setSalonName("Salon");
        request.setSalonSlug("yeni");
        request.setAdminUsername("adminx");
        request.setAdminPassword("password1");

        TenantProvisionResponse result = inviteCodeService.registerWithInvite(request);
        assertThat(result.getOrganizationId()).isEqualTo(9L);
        ArgumentCaptor<InviteCode> captor = ArgumentCaptor.forClass(InviteCode.class);
        verify(inviteCodeRepository).save(captor.capture());
        assertThat(captor.getValue().getUsedCount()).isEqualTo(1);
        assertThat(captor.getValue().getRedeemedOrganizationId()).isEqualTo(9L);
    }

    @Test
    void revokedCodeDenied() {
        InviteCode invite = InviteCode.builder()
                .code("GSCRM-AB12-CD34")
                .kind(InviteKind.PILOT)
                .maxUses(1)
                .usedCount(0)
                .revokedAt(LocalDateTime.now())
                .planCode("SOLO")
                .createdAt(LocalDateTime.now())
                .build();
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-AB12-CD34")).thenReturn(Optional.of(invite));
        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setInviteCode("GSCRM-AB12-CD34");
        assertThatThrownBy(() -> inviteCodeService.registerWithInvite(request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void exhaustedCodeDenied() {
        InviteCode invite = InviteCode.builder()
                .code("GSCRM-AB12-CD34")
                .kind(InviteKind.PILOT)
                .maxUses(1)
                .usedCount(1)
                .planCode("SOLO")
                .createdAt(LocalDateTime.now())
                .build();
        when(inviteCodeRepository.findByCodeForUpdate("GSCRM-AB12-CD34")).thenReturn(Optional.of(invite));
        TenantProvisionRequest request = new TenantProvisionRequest();
        request.setInviteCode("GSCRM-AB12-CD34");
        assertThatThrownBy(() -> inviteCodeService.registerWithInvite(request))
                .isInstanceOf(AccessDeniedException.class);
    }
}
