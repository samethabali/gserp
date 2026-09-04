package com.gscrm.controller;

import com.gscrm.dto.response.ApiResponse;
import com.gscrm.dto.response.OrgSummaryResponse;
import com.gscrm.model.Salon;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.security.BranchScopeService;
import com.gscrm.security.StaffScopeService;
import com.gscrm.service.DashboardService;
import com.gscrm.tenant.OrganizationContextService;
import com.gscrm.tenant.TenantContext;
import com.gscrm.repository.SalonRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/org")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ORG_OWNER','PLATFORM_ADMIN')")
public class OrganizationController {

    private final SalonRepository salonRepository;
    private final StaffScopeService staffScopeService;
    private final BranchScopeService branchScopeService;
    private final OrganizationContextService organizationContextService;
    private final DashboardService dashboardService;

    @GetMapping("/salons")
    public ResponseEntity<ApiResponse<List<Salon>>> listSalons() {
        AuthenticatedUser user = staffScopeService.requireAuthenticatedUser();
        Long orgId = requireOrganizationId(user);
        return ResponseEntity.ok(ApiResponse.ok(
                salonRepository.findByOrganizationIdAndActiveTrue(orgId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<OrgSummaryResponse>> summary() {
        AuthenticatedUser user = staffScopeService.requireAuthenticatedUser();
        Long orgId = requireOrganizationId(user);
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getOrgSummary(orgId)));
    }

    /**
     * Franchise sahibinin aktif şubesini değiştirir.
     *
     * <p>Seçim bir yıllık çerez yerine oturuma yazılır. Çerez, kiracı çözümlemesinde
     * adresten önce geliyordu: bir kez şube değiştiren kullanıcı, açtığı her adreste
     * o şubeyi görmeye devam ediyordu ve URL kiracıyı yanlış gösteriyordu.
     */
    @PostMapping("/switch-salon")
    public ResponseEntity<ApiResponse<Map<String, String>>> switchSalon(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        AuthenticatedUser user = staffScopeService.requireAuthenticatedUser();
        String slug = body.get("slug");
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug gerekli");
        }
        Salon salon = salonRepository.findBySlugAndActiveTrue(slug.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Salon bulunamadı"));
        branchScopeService.assertCanAccessSalon(salon.getId(), user);

        request.getSession(true).setAttribute(TenantContext.SESSION_AUTH_SALON_ID, salon.getId());

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "slug", salon.getSlug(),
                "salonId", String.valueOf(salon.getId()),
                "redirectUrl", "/")));
    }

    private Long requireOrganizationId(AuthenticatedUser user) {
        Long orgId = organizationContextService.resolveOrganizationId(user);
        if (orgId == null) {
            throw new IllegalStateException("Organizasyon bağlamı yok");
        }
        return orgId;
    }
}
