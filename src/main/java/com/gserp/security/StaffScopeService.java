package com.gserp.security;

import com.gserp.model.Appointment;
import com.gserp.model.enums.AppointmentStatus;
import com.gserp.model.enums.UserRole;
import com.gserp.repository.AppointmentRepository;
import com.gserp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StaffScopeService {

    private static final Set<AppointmentStatus> SPECIALIST_STATUS_UPDATES = EnumSet.of(
            AppointmentStatus.IN_PROGRESS,
            AppointmentStatus.COMPLETED,
            AppointmentStatus.NO_SHOW
    );

    private final AppointmentRepository appointmentRepository;

    public AuthenticatedUser requireAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AccessDeniedException("Oturum gerekli");
        }
        return user;
    }

    public boolean isSpecialist() {
        return requireAuthenticatedUser().getRole() == UserRole.SPECIALIST;
    }

    public boolean isAdminOrReceptionist() {
        UserRole role = requireAuthenticatedUser().getRole();
        return role == UserRole.ADMIN || role == UserRole.RECEPTIONIST;
    }

    public Long specialistStaffId() {
        AuthenticatedUser user = requireAuthenticatedUser();
        if (user.getRole() != UserRole.SPECIALIST) {
            return null;
        }
        if (user.getStaffId() == null) {
            throw new AccessDeniedException("Uzman hesabı personel kaydına bağlı değil");
        }
        return user.getStaffId();
    }

    public void assertCanAccessAppointment(Long appointmentId) {
        Long staffId = specialistStaffId();
        if (staffId == null) {
            return;
        }
        Appointment appointment = appointmentRepository.findByIdAndSalonId(
                        appointmentId, TenantContext.requireSalonId())
                .orElseThrow(() -> new IllegalArgumentException("Randevu bulunamadı: " + appointmentId));
        if (!staffId.equals(appointment.getStaffId())) {
            throw new AccessDeniedException("Bu randevuya erişim yetkiniz yok");
        }
    }

    public void assertSpecialistStatusChange(AppointmentStatus newStatus) {
        if (!isSpecialist()) {
            return;
        }
        if (!SPECIALIST_STATUS_UPDATES.contains(newStatus)) {
            throw new AccessDeniedException("Bu durum değişikliği için yetkiniz yok");
        }
    }
}
