package com.gscrm.model;

import com.gscrm.model.enums.BodyRegion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Vücut şablonundaki bölge kataloğu iki yerde yaşıyor: sunucuda doğrulama için
 * {@link BodyRegion}, tarayıcıda çizim için {@code body-map.js}. İkisi ayrışırsa
 * arayüzden gönderilen kod sunucuda reddedilir ya da kayıtlı bir bölge şablonda
 * hiç çizilmez — ikisi de sessizce olur. Bu test o ayrışmayı derleme zamanına yakın
 * bir noktada yakalar.
 */
class BodyRegionCatalogTest {

    private static final Path BODY_MAP_JS =
            Path.of("src", "main", "resources", "static", "js", "body-map.js");

    /** {@code { code: 'UPPER_LIP',  label: 'Üst Dudak' },} satırlarını okur. */
    private static final Pattern ENTRY =
            Pattern.compile("\\{\\s*code:\\s*'([A-Z_]+)'\\s*,\\s*label:\\s*'([^']*)'\\s*}");

    @Test
    void jsCatalogMatchesEnum() throws IOException {
        String js = Files.readString(BODY_MAP_JS, StandardCharsets.UTF_8);

        // Yalnızca BODY_REGIONS listesi okunur; dosyanın kalanında da kod geçiyor.
        int start = js.indexOf("const BODY_REGIONS = [");
        assertFalse(start < 0, "body-map.js içinde BODY_REGIONS listesi bulunamadı");
        String block = js.substring(start, js.indexOf("];", start));

        Map<String, String> fromJs = new LinkedHashMap<>();
        Matcher m = ENTRY.matcher(block);
        while (m.find()) {
            fromJs.put(m.group(1), m.group(2));
        }

        Map<String, String> fromEnum = new LinkedHashMap<>();
        for (BodyRegion region : BodyRegion.values()) {
            fromEnum.put(region.name(), region.getLabel());
        }

        assertEquals(fromEnum, fromJs,
                "body-map.js ile BodyRegion enum'u ayrışmış (kod veya etiket farkı)");

        // Sıra da önemli: yanıtlar enum sırasında dönüyor, arayüz rozetleri de
        // aynı sırayı kullanıyor.
        List<String> enumOrder = new ArrayList<>(fromEnum.keySet());
        List<String> jsOrder = new ArrayList<>(fromJs.keySet());
        assertEquals(enumOrder, jsOrder, "Bölge sırası iki katalogda farklı");
    }
}
