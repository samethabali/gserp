package com.gscrm.service;

import com.gscrm.model.Customer;
import com.gscrm.repository.CustomerRepository;
import com.gscrm.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerMatchingServiceTest {

    private static final Long SALON_ID = 1L;
    private static final String CANONICAL = "+905321234567";

    @Mock private CustomerRepository customerRepository;
    @InjectMocks private CustomerMatchingService customerMatchingService;

    @BeforeEach
    void setUp() {
        TenantContext.setSalonId(SALON_ID);
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Customer existing(Long id, String firstName, String lastName) {
        return Customer.builder()
                .id(id).salonId(SALON_ID)
                .firstName(firstName).lastName(lastName)
                .phone("0532 123 45 67").phoneNormalized(CANONICAL)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void differentSpellingsResolveToTheSameCustomer() {
        Customer ayse = existing(5L, "Ayşe", "Yılmaz");
        when(customerRepository.findBySalonIdAndPhoneNormalized(SALON_ID, CANONICAL))
                .thenReturn(List.of(ayse));

        assertThat(customerMatchingService.findByPhone("05321234567")).contains(ayse);
        assertThat(customerMatchingService.findByPhone("+90 532 123 45 67")).contains(ayse);
        assertThat(customerMatchingService.findByPhone("532 123 45 67")).contains(ayse);
    }

    /**
     * Çözümlenemeyen telefon eşleştirme anahtarı olamaz: aksi hâlde telefonu bozuk
     * yazılmış iki yabancı aynı müşteri kaydına düşerdi.
     */
    @Test
    void unnormalizablePhoneNeverMatchesAndAlwaysCreates() {
        assertThat(customerMatchingService.findByPhone("abc")).isEmpty();
        assertThat(customerMatchingService.findAllByPhone("12345")).isEmpty();

        customerMatchingService.findOrCreate("Deneme Kişi", "abc");

        verify(customerRepository, never()).findBySalonIdAndPhoneNormalized(eq(SALON_ID), anyString());
        verify(customerRepository).save(any(Customer.class));
    }

    /**
     * Public randevu formu mevcut müşterinin adını ezmemeli — aksi hâlde numarayı
     * bilen herkes o müşteriyi dışarıdan yeniden adlandırabilirdi.
     */
    @Test
    void findOrCreateDoesNotOverwriteAnExistingName() {
        Customer ayse = existing(5L, "Ayşe", "Yılmaz");
        when(customerRepository.findBySalonIdAndPhoneNormalized(SALON_ID, CANONICAL))
                .thenReturn(List.of(ayse));

        Customer result = customerMatchingService.findOrCreate("Sahte İsim", "05321234567");

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getFirstName()).isEqualTo("Ayşe");
        assertThat(result.getLastName()).isEqualTo("Yılmaz");
    }

    @Test
    void findOrCreateFillsOnlyBlankNameFields() {
        Customer stub = existing(6L, "Müşteri", "");
        stub.setFirstName("");
        when(customerRepository.findBySalonIdAndPhoneNormalized(SALON_ID, CANONICAL))
                .thenReturn(List.of(stub));

        Customer result = customerMatchingService.findOrCreate("Ayşe Yılmaz", "05321234567");

        assertThat(result.getFirstName()).isEqualTo("Ayşe");
        assertThat(result.getLastName()).isEqualTo("Yılmaz");
    }

    @Test
    void findOrCreateStoresRawPhoneAndCanonicalIsDerivedOnPersist() {
        when(customerRepository.findBySalonIdAndPhoneNormalized(SALON_ID, CANONICAL))
                .thenReturn(List.of());

        customerMatchingService.findOrCreate("Ayşe Yılmaz", "0532 123 45 67");

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        // Ham hâli saklanır: salon müşterinin yazdığını görsün.
        assertThat(captor.getValue().getPhone()).isEqualTo("0532 123 45 67");
        assertThat(captor.getValue().getFirstName()).isEqualTo("Ayşe");
    }

    /** Aynı ziyaretçi her seferinde aynı kayda düşmeli — tanıma titrememeli. */
    @Test
    void resolutionIsStableAcrossRepeatedCalls() {
        Customer first = existing(5L, "Ayşe", "Yılmaz");
        Customer second = existing(9L, "Ayse", "Yilmaz");
        when(customerRepository.findBySalonIdAndPhoneNormalized(SALON_ID, CANONICAL))
                .thenReturn(List.of(first, second));

        for (int i = 0; i < 5; i++) {
            assertThat(customerMatchingService.findByPhone("05321234567"))
                    .map(Customer::getId)
                    .contains(5L);
        }
    }

    @Test
    void duplicateGroupsExposeEveryMemberSharingANormalizedPhone() {
        when(customerRepository.findDuplicateNormalizedPhones(SALON_ID)).thenReturn(List.of(CANONICAL));
        when(customerRepository.findBySalonIdAndPhoneNormalized(SALON_ID, CANONICAL))
                .thenReturn(List.of(existing(5L, "Ayşe", "Yılmaz"), existing(9L, "Ayse", "Yilmaz")));

        List<CustomerMatchingService.DuplicateGroup> groups = customerMatchingService.findDuplicateGroups();

        assertThat(groups).singleElement()
                .satisfies(g -> {
                    assertThat(g.normalizedPhone()).isEqualTo(CANONICAL);
                    assertThat(g.members()).hasSize(2);
                });
    }
}
