package com.gscrm.service;

import com.gscrm.model.WaitlistEntry;
import com.gscrm.repository.WaitlistEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitlistService {

    private final WaitlistEntryRepository waitlistRepository;

    @Transactional
    public WaitlistEntry add(WaitlistEntry entry) {
        entry.setCreatedAt(LocalDateTime.now());
        entry.setFulfilled(false);
        return waitlistRepository.save(entry);
    }

    public List<WaitlistEntry> getActive() {
        return waitlistRepository.findByFulfilledFalse();
    }

    public List<WaitlistEntry> getAll() {
        return waitlistRepository.findAll();
    }

    @Transactional
    public void fulfill(Long id) {
        waitlistRepository.findById(id).ifPresent(e -> {
            e.setFulfilled(true);
            waitlistRepository.save(e);
        });
    }

    @Transactional
    public void delete(Long id) {
        waitlistRepository.deleteById(id);
    }

    /**
     * Check if there are waitlist entries matching a cancelled/no-show appointment.
     */
    public List<WaitlistEntry> findMatchingEntries(Long serviceId, Long staffId) {
        return waitlistRepository.findMatchingUnfulfilled(serviceId, staffId);
    }
}
