package com.gscrm.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tenant filtresinin yapısal önkoşullarını derleme zamanında korur.
 *
 * <p>Bu testler bir davranışı değil, bir <em>kuralı</em> sınar. İzolasyon iki
 * varsayıma dayanır ve ikisi de sessizce bozulabilir:
 *
 * <ol>
 *   <li>{@code salon_id} taşıyan her entity {@code @Filter} ile işaretli olmalı —
 *       yeni bir entity eklenirken unutulursa o tablo tamamen korumasız kalır.</li>
 *   <li>Repository kullanan her servis transaction içinde çalışmalı — filtre
 *       transaction başlangıcında kurulduğu için, {@code @Transactional} olmayan
 *       bir servisin sorguları filtreden geçmez.</li>
 * </ol>
 *
 * <p>İkinci kural gerçek bir sızıntının kök nedeniydi: {@code PaymentService}
 * transaction'sız olduğu için ödeme sorguları filtresiz koşuyordu.
 */
@DisplayName("Tenant filtresi kapsam koruması")
class TenantFilterCoverageTest {

    private static final Path MODEL_DIR = Path.of("src/main/java/com/gscrm/model");
    private static final Path SERVICE_DIR = Path.of("src/main/java/com/gscrm/service");

    /**
     * Filtre dışında bırakılan entity'ler — her biri bilinçli bir karardır.
     *
     * <p>{@code User}: giriş akışı zaten {@code TenantContext} ile açıkça kapsamlıdır
     * ({@code findBySalonIdAndUsername}); filtre eklemek platform yöneticisinin
     * yanlış tenant URL'inden giriş yapamamasına, yani kilitlenmeye yol açar.
     *
     * <p>{@code UsageMeter}: salon değil organizasyon kapsamlıdır — kota sayaçları
     * org genelinde toplanır, salona kısıtlamak faturalandırmayı bozar.
     */
    private static final List<String> INTENTIONALLY_UNFILTERED = List.of("User", "UsageMeter");

    @Test
    @DisplayName("salon_id taşıyan her entity @Filter ile işaretli")
    void everyTenantEntityIsFiltered() throws IOException {
        List<String> unprotected = new ArrayList<>();

        try (Stream<Path> files = Files.list(MODEL_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                if (name.equals("package-info") || INTENTIONALLY_UNFILTERED.contains(name)) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (!source.contains("name = \"salon_id\"")) {
                    continue;
                }
                if (!source.contains("@Filter(") || !source.contains("implements TenantEntity")) {
                    unprotected.add(name);
                }
            }
        }

        assertThat(unprotected)
                .as("salon_id taşıyan entity'ler @Filter + TenantEntity gerektirir. "
                        + "Bilerek muaf tutuluyorsa INTENTIONALLY_UNFILTERED listesine gerekçesiyle ekleyin.")
                .isEmpty();
    }

    /**
     * Kural yalnızca {@code com.gscrm.service} paketine uygulanır: sızıntının
     * gerçekleştiği katman burasıdır. Güvenlik paketindeki filtre/yardımcı sınıflar
     * repository'ye erişirken kendi açık salon kapsamlarını kullanır ve servlet
     * filtresi olarak Spring transaction proxy'sine tabi değildir.
     */
    @Test
    @DisplayName("Repository kullanan her servis transaction sınırına sahip")
    void everyRepositoryUsingServiceIsTransactional() throws IOException {
        List<String> nonTransactional = new ArrayList<>();

        try (Stream<Path> files = Files.list(SERVICE_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (!source.contains("Repository ")) {
                    continue;
                }
                String beforeClass = source.substring(0, source.indexOf("public class "));
                if (!beforeClass.contains("@Transactional")) {
                    nonTransactional.add(file.getFileName().toString().replace(".java", ""));
                }
            }
        }

        assertThat(nonTransactional)
                .as("Bu servisler repository kullanıyor ama sınıf düzeyinde @Transactional taşımıyor. "
                        + "Transaction olmadan Hibernate tenant filtresi kurulmaz ve sorgular "
                        + "filtresiz koşar — bu, gerçek bir cross-tenant sızıntısının kök nedeniydi.")
                .isEmpty();
    }
}
