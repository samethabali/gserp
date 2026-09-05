package com.gscrm.service;

import com.gscrm.model.ActivityEvent;
import com.gscrm.model.InviteCode;
import com.gscrm.model.InviteRedemption;
import com.gscrm.model.Organization;
import com.gscrm.model.OrganizationSubscription;
import com.gscrm.model.Salon;
import com.gscrm.model.SubscriptionPlan;
import com.gscrm.repository.ActivityEventRepository;
import com.gscrm.repository.AppointmentRepository;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.repository.InviteCodeRepository;
import com.gscrm.repository.ImpersonationLogRepository;
import com.gscrm.repository.InviteRedemptionRepository;
import com.gscrm.repository.OrganizationRepository;
import com.gscrm.repository.OrganizationSubscriptionRepository;
import com.gscrm.repository.SalonRepository;
import com.gscrm.repository.SubscriptionPlanRepository;
import com.gscrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Platform panelinin okuma tarafı.
 *
 * <p>Panel daha önce yalnızca salon adı ve slug gösteriyordu; "bu işletme hangi
 * planda, denemesi ne zaman bitiyor, kaç randevu girmiş, hangi davet koduyla geldi"
 * sorularının hiçbiri yanıtlanamıyordu. Tüm ek alanlar toplu sorgularla çözülür:
 * liste eskiden her satır için ayrı bir {@code findById} çağırıyordu.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformOverviewService {

    private static final int DETAIL_ACTIVITY_LIMIT = 100;

    private final SalonRepository salonRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final ActivityEventRepository activityEventRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final InviteRedemptionRepository inviteRedemptionRepository;
    private final ImpersonationLogRepository impersonationLogRepository;

    /** Kiracı listesi: tüm salonlar, zenginleştirilmiş ve sabit sayıda sorguyla. */
    public List<Map<String, Object>> listTenants() {
        List<Salon> salons = salonRepository.findAll();
        if (salons.isEmpty()) {
            return List.of();
        }
        List<Long> salonIds = salons.stream().map(Salon::getId).toList();
        Set<Long> orgIds = salons.stream().map(Salon::getOrganizationId).collect(Collectors.toSet());

        Map<Long, Organization> orgs = organizationRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));
        Map<Long, OrganizationSubscription> subs = subscriptionRepository.findByOrganizationIdIn(orgIds).stream()
                .collect(Collectors.toMap(OrganizationSubscription::getOrganizationId, Function.identity()));
        Map<Long, SubscriptionPlan> plans = subscriptionPlanRepository.findAll().stream()
                .collect(Collectors.toMap(SubscriptionPlan::getId, Function.identity()));

        Map<Long, Long> userCounts = toCountMap(userRepository.countGroupedBySalonIds(salonIds));
        Map<Long, Long> customerCounts = toCountMap(customerRepository.countGroupedBySalonIds(salonIds));
        Map<Long, Long> appointmentCounts = toCountMap(appointmentRepository.countGroupedBySalonIds(salonIds));
        Map<Long, LocalDateTime> lastActivity = toTimeMap(
                activityEventRepository.findLastActivityGroupedBySalonIds(salonIds));
        Map<Long, Long> adminUserIds = toCountMap(userRepository.findAdminUserIdsBySalonIds(salonIds));

        Map<Long, InviteRedemption> redemptionByOrg = inviteRedemptionRepository.findByOrganizationIdIn(orgIds)
                .stream()
                .collect(Collectors.toMap(InviteRedemption::getOrganizationId, Function.identity(), (a, b) -> a));
        Map<Long, String> inviteCodes = inviteCodeRepository.findAllById(
                        redemptionByOrg.values().stream().map(InviteRedemption::getInviteCodeId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(InviteCode::getId, InviteCode::getCode));

        List<Map<String, Object>> rows = new ArrayList<>(salons.size());
        for (Salon salon : salons) {
            Organization org = orgs.get(salon.getOrganizationId());
            OrganizationSubscription sub = subs.get(salon.getOrganizationId());
            InviteRedemption redemption = redemptionByOrg.get(salon.getOrganizationId());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("salonId", salon.getId());
            row.put("slug", salon.getSlug());
            row.put("name", salon.getName());
            row.put("active", salon.isActive());
            row.put("showcase", salon.isShowcase());
            row.put("createdAt", salon.getCreatedAt());
            row.put("contactEmail", salon.getContactEmail());
            row.put("organizationId", salon.getOrganizationId());
            row.put("organizationName", org != null ? org.getName() : null);
            row.put("organizationType", org != null && org.getType() != null ? org.getType().name() : null);

            row.put("planCode", sub != null ? planCode(plans, sub.getPlanId()) : null);
            row.put("subscriptionStatus", sub != null ? sub.getStatus() : null);
            row.put("trialEnd", sub != null ? sub.getTrialEnd() : null);
            row.put("trialDaysRemaining", remainingDays(sub));
            row.put("statusBadge", statusBadge(salon, sub));

            row.put("userCount", userCounts.getOrDefault(salon.getId(), 0L));
            row.put("customerCount", customerCounts.getOrDefault(salon.getId(), 0L));
            row.put("appointmentCount", appointmentCounts.getOrDefault(salon.getId(), 0L));
            row.put("lastActivityAt", lastActivity.get(salon.getId()));

            row.put("inviteCode", redemption != null ? inviteCodes.get(redemption.getInviteCodeId()) : null);
            row.put("invitedAt", redemption != null ? redemption.getRedeemedAt() : null);
            // Panelin "hesabına gir" butonu hedefi; yoksa buton devre dışı kalır.
            row.put("adminUserId", adminUserIds.get(salon.getId()));
            rows.add(row);
        }
        return rows;
    }

    /** Tek kiracının detayı ve son {@value #DETAIL_ACTIVITY_LIMIT} işlemi. */
    public Map<String, Object> tenantDetail(Long salonId) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new IllegalArgumentException("Salon bulunamadı"));
        Map<String, Object> detail = listTenants().stream()
                .filter(row -> salonId.equals(row.get("salonId")))
                .findFirst()
                .orElseGet(HashMap::new);

        Map<String, Object> result = new LinkedHashMap<>(detail);
        result.put("timezone", salon.getTimezone());
        result.put("activity", activityEventRepository.findBySalonIdOrderByCreatedAtDesc(
                salonId, PageRequest.of(0, DETAIL_ACTIVITY_LIMIT)));
        result.put("impersonations", impersonationLogRepository.findBySalonIdOrderByStartedAtDesc(
                salonId, PageRequest.of(0, 20)));
        return result;
    }

    /** Platform kapsamı dahil global akış; {@code salonId} verilirse tek kiracıya daralır. */
    public List<ActivityEvent> activityFeed(Long salonId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        PageRequest page = PageRequest.of(0, capped);
        return salonId != null
                ? activityEventRepository.findBySalonIdOrderByCreatedAtDesc(salonId, page)
                : activityEventRepository.findAllByOrderByCreatedAtDesc(page);
    }

    /**
     * Bir davet kodunun tüm kullanımları.
     *
     * <p>Panel eskiden tek bir {@code redeemedOrganizationName} gösteriyordu ve
     * salonları {@code findByOrganizationIdAndActiveTrue} ile çekiyordu: askıya
     * alınan bir işletme davet kodu satırından tamamen kayboluyordu.
     */
    public Map<Long, List<Map<String, Object>>> redemptionsByInvite(Collection<Long> inviteCodeIds) {
        if (inviteCodeIds.isEmpty()) {
            return Map.of();
        }
        List<InviteRedemption> redemptions =
                inviteRedemptionRepository.findByInviteCodeIdInOrderByRedeemedAtDesc(inviteCodeIds);
        if (redemptions.isEmpty()) {
            return Map.of();
        }
        Set<Long> orgIds = redemptions.stream().map(InviteRedemption::getOrganizationId).collect(Collectors.toSet());
        Map<Long, String> orgNames = organizationRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getName));
        Set<Long> salonIds = redemptions.stream().map(InviteRedemption::getSalonId).collect(Collectors.toSet());
        Map<Long, Boolean> salonActive = salonRepository.findAllById(salonIds).stream()
                .collect(Collectors.toMap(Salon::getId, Salon::isActive));

        Map<Long, List<Map<String, Object>>> byInvite = new LinkedHashMap<>();
        for (InviteRedemption redemption : redemptions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("organizationId", redemption.getOrganizationId());
            row.put("organizationName", orgNames.get(redemption.getOrganizationId()));
            row.put("salonId", redemption.getSalonId());
            row.put("salonSlug", redemption.getSalonSlug());
            row.put("redeemedAt", redemption.getRedeemedAt());
            row.put("active", salonActive.getOrDefault(redemption.getSalonId(), Boolean.FALSE));
            byInvite.computeIfAbsent(redemption.getInviteCodeId(), k -> new ArrayList<>()).add(row);
        }
        return byInvite;
    }

    private String planCode(Map<Long, SubscriptionPlan> plans, Long planId) {
        SubscriptionPlan plan = plans.get(planId);
        return plan != null ? plan.getCode() : null;
    }

    private Long remainingDays(OrganizationSubscription sub) {
        if (sub == null || sub.getTrialEnd() == null) {
            return null;
        }
        long days = Duration.between(LocalDateTime.now(), sub.getTrialEnd()).toDays();
        return Math.max(days, 0);
    }

    /** Panelde tek rozet; askıya alma abonelik durumunun önüne geçer. */
    private String statusBadge(Salon salon, OrganizationSubscription sub) {
        if (!salon.isActive()) {
            return "SUSPENDED";
        }
        if (sub == null) {
            return "NO_SUBSCRIPTION";
        }
        if ("TRIAL".equals(sub.getStatus())) {
            boolean expired = sub.getTrialEnd() != null && sub.getTrialEnd().isBefore(LocalDateTime.now());
            return expired ? "TRIAL_EXPIRED" : "TRIAL";
        }
        return sub.getStatus();
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    private Map<Long, LocalDateTime> toTimeMap(List<Object[]> rows) {
        Map<Long, LocalDateTime> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                map.put((Long) row[0], (LocalDateTime) row[1]);
            }
        }
        return map;
    }
}
