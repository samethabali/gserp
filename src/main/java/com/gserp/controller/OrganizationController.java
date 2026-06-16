package com.gserp.controller;

import com.gserp.dto.response.ApiResponse;
import com.gserp.dto.response.OrgSummaryResponse;
import com.gserp.model.Salon;
import com.gserp.model.enums.AppointmentStatus;
import com.gserp.repository.AppointmentRepository;
import com.gserp.repository.OrganizationRepository;
import com.gserp.repository.SalonRepository;
import com.gserp.security.AuthenticatedUser;
import com.gserp.security.StaffScopeService;
import com.gserp.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/org")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ORG_OWNER','PLATFORM_ADMIN')")
public class OrganizationController {

    private final SalonRepository salonRepository;
    private final OrganizationRepository organizationRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffScopeService staffScopeService;
    private final DashboardService dashboardService;

    @GetMapping("/salons")
    public ResponseEntity<ApiResponse<List<Salon>>> listSalons() {
        AuthenticatedUser user = staffScopeService.requireAuthenticatedUser();
        Long orgId = user.getOrganizationId();
        if (orgId == null) {
            throw new IllegalStateException("Organizasyon bağlamı yok");
        }
        return ResponseEntity.ok(ApiResponse.ok(
                salonRepository.findByOrganizationIdAndActiveTrue(orgId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<OrgSummaryResponse>> summary() {
        AuthenticatedUser user = staffScopeService.requireAuthenticatedUser();
        Long orgId = user.getOrganizationId();
        if (orgId == null) {
            throw new IllegalStateException("Organizasyon bağlamı yok");
        }
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getOrgSummary(orgId)));
    }
}
