package com.gscrm.model.enums;

/**
 * Epilasyon randevularında işlem yapılacak vücut bölgeleri.
 *
 * <p>Katalog sunucuda tutulur çünkü gelen kodun doğrulanması gerekir: serbest metin
 * kabul edilseydi randevu detayında ne anlama geldiği belirsiz bir etiket
 * gösterilebilirdi. Arayüzdeki şablon ({@code static/js/body-map.js}) bu enum ile
 * birebir aynı kodları kullanır; {@code BodyRegionCatalogTest} eşleşmeyi bekçiler.
 *
 * <p>Sıralama baştan ayağa doğrudur — yanıtlar bu sırayla döner, böylece bölge
 * listesi her yerde aynı okunur.
 */
public enum BodyRegion {

    UPPER_LIP("Üst Dudak"),
    CHIN("Çene"),
    FACE("Yüz"),
    NECK("Boyun"),
    NAPE("Ense"),
    UNDERARM("Koltuk Altı"),
    UPPER_ARM("Üst Kol"),
    FOREARM("Ön Kol"),
    HAND("El"),
    CHEST("Göğüs"),
    ABDOMEN("Karın"),
    UPPER_BACK("Sırt"),
    LOWER_BACK("Bel"),
    BIKINI("Bikini"),
    BUTTOCKS("Kalça"),
    THIGH("Üst Bacak"),
    LOWER_LEG("Alt Bacak"),
    FOOT("Ayak");

    private final String label;

    BodyRegion(String label) {
        this.label = label;
    }

    /** Panelde ve müşteri ekranlarında gösterilen Türkçe ad. */
    public String getLabel() {
        return label;
    }
}
