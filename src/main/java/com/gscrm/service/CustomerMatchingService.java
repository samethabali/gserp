package com.gscrm.service;

import com.gscrm.model.Customer;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.tenant.TenantContext;
import com.gscrm.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Telefondan müşteriye giden <b>tek</b> yol.
 *
 * <p>Eskiden her akış kendi ham string eşitliğini yapıyordu; formatı farklı yazılan
 * aynı numara farklı müşteriye düşüyordu. Buradaki eşleştirme kanonik telefon
 * üzerinden çalışır ve çözümlenemeyen girdide bilerek <b>hiç</b> eşleşmez.
 *
 * <p>Bağımlılık yalnızca {@link CustomerRepository}: {@code ActivityEventService}
 * bu servisin çağıranıdır, buraya enjekte edilseydi döngü olurdu.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerMatchingService {

    private final CustomerRepository customerRepository;

    /** Normalize telefonu paylaşan müşteri grubu — panelin yinelenen uyarısı için. */
    public record DuplicateGroup(String normalizedPhone, List<Customer> members) {
    }

    /**
     * Bu telefona karşılık gelen müşteri. Birden çok aday varsa kararlı sıralamanın
     * ilki döner (bkz. {@code CustomerRepository#findBySalonIdAndPhoneNormalized}).
     */
    public Optional<Customer> findByPhone(String rawPhone) {
        List<Customer> matches = findAllByPhone(rawPhone);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    /** Bu telefonu paylaşan tüm müşteriler; çözümlenemeyen girdide boş liste. */
    public List<Customer> findAllByPhone(String rawPhone) {
        String normalized = PhoneNormalizer.normalizeOrNull(rawPhone);
        if (normalized == null) return List.of();
        return customerRepository.findBySalonIdAndPhoneNormalized(TenantContext.requireSalonId(), normalized);
    }

    /**
     * Mevcut müşteriyi bulur, yoksa oluşturur.
     *
     * <p>Eşleşme bulunduğunda ad/soyad <b>üzerine yazılmaz</b>: bu metot public
     * randevu formundan da çağrılıyor, aksi hâlde numarayı bilen herkes o müşteriyi
     * dışarıdan yeniden adlandırabilirdi. Yalnızca boş alanlar doldurulur.
     *
     * @param displayName formdan gelen "Ad Soyad"
     * @param rawPhone    kullanıcının yazdığı hâliyle telefon
     */
    @Transactional
    public Customer findOrCreate(String displayName, String rawPhone) {
        Long salonId = TenantContext.requireSalonId();
        String normalized = PhoneNormalizer.normalizeOrNull(rawPhone);
        String[] parts = splitName(displayName);

        // Çözümlenemeyen telefon asla eşleştirme anahtarı olamaz — her seferinde yeni kayıt.
        if (normalized == null) {
            return customerRepository.save(newCustomer(salonId, parts, rawPhone));
        }

        Optional<Customer> existing = customerRepository
                .findBySalonIdAndPhoneNormalized(salonId, normalized).stream().findFirst();

        if (existing.isPresent()) {
            Customer customer = existing.get();
            boolean touched = false;
            if (isBlank(customer.getFirstName()) && !isBlank(parts[0])) {
                customer.setFirstName(parts[0]);
                touched = true;
            }
            if (isBlank(customer.getLastName()) && !isBlank(parts[1])) {
                customer.setLastName(parts[1]);
                touched = true;
            }
            // Eski kayıtlar kendini iyileştirsin (@PreUpdate zaten üretecek).
            if (customer.getPhoneNormalized() == null) {
                touched = true;
            }
            if (touched) {
                customer.setUpdatedAt(LocalDateTime.now());
                return customerRepository.save(customer);
            }
            return customer;
        }

        try {
            return customerRepository.save(newCustomer(salonId, parts, rawPhone));
        } catch (DataIntegrityViolationException e) {
            // Eşzamanlı istek önce yazmış olabilir (ya da ileride unique index eklenirse).
            return customerRepository.findBySalonIdAndPhoneNormalized(salonId, normalized).stream()
                    .findFirst()
                    .orElseThrow(() -> e);
        }
    }

    /** Aynı normalize telefonu paylaşan müşteri grupları — birleştirme kararı salonun. */
    public List<DuplicateGroup> findDuplicateGroups() {
        Long salonId = TenantContext.requireSalonId();
        List<DuplicateGroup> groups = new ArrayList<>();
        for (String normalized : customerRepository.findDuplicateNormalizedPhones(salonId)) {
            List<Customer> members = customerRepository.findBySalonIdAndPhoneNormalized(salonId, normalized);
            if (members.size() > 1) {
                groups.add(new DuplicateGroup(normalized, members));
            }
        }
        return groups;
    }

    private Customer newCustomer(Long salonId, String[] parts, String rawPhone) {
        LocalDateTime now = LocalDateTime.now();
        return Customer.builder()
                .salonId(salonId)
                .homeSalonId(salonId)
                .firstName(parts[0])
                .lastName(parts[1])
                // Ham hâli saklanır: salon müşterinin yazdığını görsün.
                .phone(rawPhone != null ? rawPhone.trim() : null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"Müşteri", ""};
        }
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[]{trimmed, ""};
        }
        return new String[]{trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }
}
