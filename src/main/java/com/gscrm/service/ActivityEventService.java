package com.gscrm.service;

import com.gscrm.model.ActivityEvent;
import com.gscrm.repository.ActivityEventRepository;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.security.AuthenticatedUser;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityEventService {

    private final ActivityEventRepository activityEventRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public void record(String action, String entityType, Long entityId, Long customerId,
                       String summary, String detail, String ip) {
        Long salonId = TenantContext.getSalonId();
        if (salonId == null) {
            return;
        }
        AuthenticatedUser actor = currentUser();
        ActivityEvent event = ActivityEvent.builder()
                .salonId(salonId)
                .customerId(customerId)
                .actorUserId(actor != null ? actor.getId() : null)
                .actorUsername(actor != null ? actor.getUsername() : "anonim")
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .summary(summary != null ? truncate(summary, 512) : action)
                .detail(detail)
                .ip(ip)
                .createdAt(LocalDateTime.now())
                .build();
        activityEventRepository.save(event);
    }

    @Transactional
    public void record(String action, String entityType, Long entityId, Long customerId, String summary) {
        record(action, entityType, entityId, customerId, summary, null, null);
    }

    @Transactional
    public void recordForCustomerPhone(String action, String entityType, Long entityId,
                                       String customerPhone, String summary) {
        Long customerId = null;
        Long salonId = TenantContext.getSalonId();
        if (salonId != null && customerPhone != null && !customerPhone.isBlank()) {
            customerId = customerRepository.findBySalonIdAndPhone(salonId, customerPhone)
                    .map(c -> c.getId())
                    .orElse(null);
        }
        record(action, entityType, entityId, customerId, summary);
    }

    @Transactional(readOnly = true)
    public List<ActivityEvent> listRecent(int limit) {
        Long salonId = TenantContext.requireSalonId();
        int capped = Math.min(Math.max(limit, 1), 200);
        return activityEventRepository.findBySalonIdOrderByCreatedAtDesc(salonId, PageRequest.of(0, capped));
    }

    @Transactional(readOnly = true)
    public List<ActivityEvent> listForCustomer(Long customerId, int limit) {
        Long salonId = TenantContext.requireSalonId();
        int capped = Math.min(Math.max(limit, 1), 200);
        return activityEventRepository.findBySalonIdAndCustomerIdOrderByCreatedAtDesc(
                salonId, customerId, PageRequest.of(0, capped));
    }

    private AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
