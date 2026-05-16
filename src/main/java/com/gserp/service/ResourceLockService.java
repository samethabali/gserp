package com.gserp.service;

import com.gserp.exception.ResourceNotAvailableException;
import com.gserp.model.Appointment;
import com.gserp.model.Resource;
import com.gserp.model.ServiceDefinition;
import com.gserp.model.enums.AppointmentStatus;
import com.gserp.model.enums.ResourceType;
import com.gserp.repository.AppointmentRepository;
import com.gserp.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourceLockService {

    private final ResourceRepository resourceRepository;
    private final AppointmentRepository appointmentRepository;

    /**
     * For a service that requires resources, find an available resource from the required list
     * in the given time range. Returns the IDs of locked resources.
     *
     * The service's requiredResourceIds can represent ALTERNATIVES (e.g., Cilt Bakım Odası 1 OR 2)
     * or ALL REQUIRED (e.g., Lazer Odası AND Lazer Cihazı).
     *
     * Strategy: group by resource type. Within same type → pick one available.
     * Across types → all must be available.
     */
    public List<Long> validateAndLock(ServiceDefinition service, LocalDateTime start, LocalDateTime end,
                                       Long excludeAppointmentId) {
        if (!service.isRequiresResource() || service.getRequiredResourceIds().isEmpty()) {
            return List.of();
        }

        List<Long> lockedIds = new ArrayList<>();

        // Group required resources by type
        var byType = new LinkedHashMap<ResourceType, List<Resource>>();
        for (Long resId : service.getRequiredResourceIds()) {
            resourceRepository.findById(resId).ifPresent(r ->
                    byType.computeIfAbsent(r.getResourceType(), k -> new ArrayList<>()).add(r));
        }

        for (var entry : byType.entrySet()) {
            List<Resource> candidates = entry.getValue();
            boolean found = false;

            for (Resource candidate : candidates) {
                if (isResourceAvailable(candidate, start, end, excludeAppointmentId)) {
                    lockedIds.add(candidate.getId());
                    found = true;
                    break; // one per type is enough
                }
            }

            if (!found) {
                String typeNames = candidates.stream().map(Resource::getName)
                        .reduce((a, b) -> a + " / " + b).orElse("Kaynak");
                throw new ResourceNotAvailableException(
                        typeNames + " bu saatte müsait değil (" +
                        start.toLocalTime() + " - " + end.toLocalTime() + ")");
            }
        }

        return lockedIds;
    }

    private boolean isResourceAvailable(Resource resource, LocalDateTime start, LocalDateTime end,
                                         Long excludeAppointmentId) {
        List<Appointment> conflicts = appointmentRepository.findResourceOverlap(
                resource.getId(), start, end, AppointmentStatus.CANCELLED);
        if (excludeAppointmentId != null) {
            conflicts = conflicts.stream()
                    .filter(a -> !a.getId().equals(excludeAppointmentId))
                    .toList();
        }
        return conflicts.size() < resource.getCapacity();
    }
}
