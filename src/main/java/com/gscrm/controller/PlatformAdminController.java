package com.gscrm.controller;

import com.gscrm.dto.request.InviteCreateRequest;
import com.gscrm.dto.request.TenantProvisionRequest;
import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.TenantProvisionResponse;
import com.gscrm.model.ActivityEvent;
import com.gscrm.model.InviteCode;
import com.gscrm.model.Salon;
import com.gscrm.repository.SalonRepository;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.security.ClientIpResolver;
import com.gscrm.security.ImpersonationService;
import com.gscrm.security.StaffScopeService;
import com.gscrm.service.ActivityEventService;
import com.gscrm.service.InviteCodeService;
import com.gscrm.service.PlatformOverviewService;
import com.gscrm.service.SalonProvisioningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminController {

    private final SalonRepository salonRepository;
    private final SalonProvisioningService provisioningService;
    private final ImpersonationService impersonationService;
    private final StaffScopeService staffScopeService;
    private final InviteCodeService inviteCodeService;
    private final PlatformOverviewService platformOverviewService;
    private final ActivityEventService activityEventService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listTenants() {
        return ResponseEntity.ok(ApiResponse.ok(platformOverviewService.listTenants()));
    }

    @GetMapping("/tenants/{salonId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> tenantDetail(@PathVariable Long salonId) {
        return ResponseEntity.ok(ApiResponse.ok(platformOverviewService.tenantDetail(salonId)));
    }

    @PostMapping("/tenants")
    public ResponseEntity<ApiResponse<TenantProvisionResponse>> provision(
            @Valid @RequestBody TenantProvisionRequest request,
            HttpServletRequest httpRequest) {
        TenantProvisionResponse result = provisioningService.provision(request);
        activityEventService.recordPlatform("CREATE", "TENANT", result.getSalonId(),
                "Kiracı açıldı: " + result.getSalonSlug(), null, clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok("Tenant oluşturuldu", result));
    }

    @PatchMapping("/tenants/{salonId}/suspend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> suspend(@PathVariable Long salonId,
                                                                   @RequestBody Map<String, Boolean> body,
                                                                   HttpServletRequest httpRequest) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new IllegalArgumentException("Salon bulunamadı"));
        boolean active = body.getOrDefault("active", false);
        salon.setActive(active);
        salonRepository.save(salon);
        activityEventService.recordPlatform(active ? "ACTIVATE" : "SUSPEND", "TENANT", salon.getId(),
                (active ? "Kiracı açıldı: " : "Kiracı askıya alındı: ") + salon.getSlug(),
                null, clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "salonId", salon.getId(),
                "slug", salon.getSlug(),
                "active", salon.isActive())));
    }

    @PostMapping("/impersonate/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> impersonate(
            @PathVariable Long userId,
            HttpServletRequest request) {
        AuthenticatedUser admin = staffScopeService.requireAuthenticatedUser();
        String redirect = impersonationService.startImpersonation(admin, userId, request);
        activityEventService.recordPlatform("IMPERSONATE_START", "USER", userId,
                "Hesabına girildi: kullanıcı #" + userId, null, clientIpResolver.resolve(request));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("redirectUrl", redirect)));
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<List<ActivityEvent>>> activity(
            @RequestParam(required = false) Long salonId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(platformOverviewService.activityFeed(salonId, limit)));
    }

    @GetMapping("/invites")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listInvites() {
        List<InviteCode> invites = inviteCodeService.list();
        Map<Long, List<Map<String, Object>>> redemptions = platformOverviewService.redemptionsByInvite(
                invites.stream().map(InviteCode::getId).toList());

        List<Map<String, Object>> rows = invites.stream().map(invite -> {
            Map<String, Object> row = new HashMap<>(inviteCodeService.toMap(invite));
            row.put("redemptions", redemptions.getOrDefault(invite.getId(), List.of()));
            return row;
        }).toList();
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @PostMapping("/invites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createInvite(
            @Valid @RequestBody InviteCreateRequest request,
            HttpServletRequest httpRequest) {
        AuthenticatedUser admin = staffScopeService.requireAuthenticatedUser();
        InviteCode created = inviteCodeService.create(request, admin.getId());
        activityEventService.recordPlatform("CREATE", "INVITE_CODE", created.getId(),
                "Davet kodu oluşturuldu: " + created.getCode() + " (" + created.getTrialDays() + " gün)",
                null, clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok("Davet kodu oluşturuldu", inviteCodeService.toMap(created)));
    }

    @PostMapping("/invites/{id}/revoke")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeInvite(@PathVariable Long id,
                                                                        HttpServletRequest httpRequest) {
        InviteCode revoked = inviteCodeService.revoke(id);
        activityEventService.recordPlatform("REVOKE", "INVITE_CODE", revoked.getId(),
                "Davet kodu iptal edildi: " + revoked.getCode(), null, clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok("Davet kodu iptal edildi", inviteCodeService.toMap(revoked)));
    }
}
