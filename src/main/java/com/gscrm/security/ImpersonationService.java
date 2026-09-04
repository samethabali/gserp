package com.gscrm.security;

import com.gscrm.model.ImpersonationLog;
import com.gscrm.model.User;
import com.gscrm.repository.ImpersonationLogRepository;
import com.gscrm.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ImpersonationService {

    public static final String SESSION_IMPERSONATOR_ID = "GSCRM_IMPERSONATOR_ID";

    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;
    private final ImpersonationLogRepository impersonationLogRepository;

    @Transactional
    public String startImpersonation(AuthenticatedUser platformAdmin, Long targetUserId, HttpServletRequest request) {
        if (platformAdmin.getRole() != com.gscrm.model.enums.UserRole.PLATFORM_ADMIN) {
            throw new AccessDeniedException("Yalnızca platform admin impersonation yapabilir");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı"));
        UserDetails targetDetails = userDetailsService.loadUserByUsername(target.getUsername());

        impersonationLogRepository.save(ImpersonationLog.builder()
                .platformUserId(platformAdmin.getId())
                .targetUserId(targetUserId)
                .salonId(target.getSalonId())
                .startedAt(LocalDateTime.now())
                .build());

        request.getSession().setAttribute(SESSION_IMPERSONATOR_ID, platformAdmin.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(targetDetails, null, targetDetails.getAuthorities()));

        return "/";
    }

    /**
     * Açık impersonation kaydını kapatır.
     *
     * <p>{@code ended_at} hiçbir zaman yazılmıyordu: kütükte her oturum süresiz
     * açık görünüyor, "platform admin bu hesapta ne kadar kaldı" sorusu
     * yanıtsız kalıyordu. Çıkışta çağrılır.
     */
    @Transactional
    public void endImpersonation(Long platformUserId) {
        if (platformUserId == null) {
            return;
        }
        impersonationLogRepository
                .findFirstByPlatformUserIdAndEndedAtIsNullOrderByStartedAtDesc(platformUserId)
                .ifPresent(log -> {
                    log.setEndedAt(LocalDateTime.now());
                    impersonationLogRepository.save(log);
                });
    }
}
