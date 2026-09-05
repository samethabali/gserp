package com.gscrm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Statik dosya adreslerine eklenen sürüm damgası.
 *
 * <p>CSS ve JS artık tarayıcıda yedi gün duruyor ({@code WebConfig}); önbelleğin
 * yeni sürümü kaçırmaması adresin değişmesine bağlı. Sürüm her derlemede
 * yenilendiği için ({@code pom.xml -> build.timestamp}) deploy sonrası adres
 * kendiliğinden tazeleniyor.
 *
 * <p>Değer daha önce her şablonda {@code @environment.getProperty(...)} ile tek
 * tek okunuyordu; yeni bir sayfa eklerken bunu yazmayı unutmak, o sayfanın
 * dosyasını sürümsüz — yani önbellek açıldığında güncellenemez — bırakırdı.
 */
@ControllerAdvice
public class AssetVersionAdvice {

    private final String assetVersion;

    public AssetVersionAdvice(@Value("${app.asset-version:1}") String assetVersion) {
        this.assetVersion = assetVersion;
    }

    @ModelAttribute
    public void addAssetVersion(Model model) {
        model.addAttribute("assetVersion", assetVersion);
    }
}
