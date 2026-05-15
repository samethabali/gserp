package com.gserp.service;

import com.gserp.model.WaitlistEntry;
import com.gserp.store.MockDataStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final MockDataStore store;

    public WaitlistEntry add(WaitlistEntry entry) {
        entry.setCreatedAt(LocalDateTime.now());
        entry.setFulfilled(false);
        return store.saveWaitlistEntry(entry);
    }

    public List<WaitlistEntry> getActive() {
        return store.findActiveWaitlistEntries();
    }

    public List<WaitlistEntry> getAll() {
        return store.findAllWaitlistEntries();
    }

    public void fulfill(Long id) {
        store.findWaitlistEntryById(id).ifPresent(e -> {
            e.setFulfilled(true);
            store.saveWaitlistEntry(e);
        });
    }

    public void delete(Long id) {
        store.deleteWaitlistEntry(id);
    }

    /**
     * Check if there are waitlist entries matching a cancelled/no-show appointment.
     */
    public List<WaitlistEntry> findMatchingEntries(Long serviceId, Long staffId) {
        return store.findActiveWaitlistEntries().stream()
                .filter(e -> (e.getServiceId() != null && e.getServiceId().equals(serviceId))
                           || (e.getPreferredStaffId() != null && e.getPreferredStaffId().equals(staffId)))
                .toList();
    }
}
