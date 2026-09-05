package com.gscrm.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * İstemci IP çözümlemesi.
 *
 * <p>Bu değere beş ayrı sayaç bağlı: giriş, kayıt, OTP, randevu yazma ve günlük
 * randevu kötüye kullanımı. Sahtelenebilen bir IP bunların hepsini birden
 * işlevsiz bırakır; bu yüzden sahteleme denemeleri tek tek sabitleniyor.
 */
@DisplayName("İstemci IP çözümlemesi")
class ClientIpResolverTest {

    private static final String PROXY = "127.0.0.1";
    private static final String REAL_CLIENT = "88.20.30.40";
    private static final String SPOOFED = "9.9.9.9";

    private final ClientIpResolver resolver = new ClientIpResolver("");

    @Test
    @DisplayName("doğrudan gelen istekte başlıklara hiç bakılmaz")
    void directRequestIgnoresHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(REAL_CLIENT);
        request.addHeader("X-Forwarded-For", SPOOFED);
        request.addHeader("CF-Connecting-IP", SPOOFED);

        assertThat(resolver.resolve(request))
                .as("vekil arkasında olmayan istekte başlıklar tamamen istemci kontrolünde")
                .isEqualTo(REAL_CLIENT);
    }

    /**
     * Vekiller {@code X-Forwarded-For}'a ekleme yapar, üzerine yazmaz: istemci
     * kendi uydurduğunu başa koyabilir. Güvenilir olan son halkadır.
     */
    @Test
    @DisplayName("istemcinin uydurduğu ilk halka değil, vekilin yazdığı son halka alınır")
    void takesNearestHopNotClientSuppliedFirstHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);
        request.addHeader("X-Forwarded-For", SPOOFED + ", " + REAL_CLIENT);

        assertThat(resolver.resolve(request))
                .as("baştaki halka alınsaydı her istekte farklı sahte adresle sınır atlanırdı")
                .isEqualTo(REAL_CLIENT);
    }

    /**
     * Cloudflare zinciri: istemci -> Cloudflare -> nginx -> uygulama.
     * Son halka Cloudflare'in kendi adresi olur; ona bakılsaydı bütün
     * ziyaretçiler tek sayaca düşerdi.
     */
    @Test
    @DisplayName("Cloudflare başlığı zincire tercih edilir")
    void cloudflareHeaderWinsOverForwardedChain() {
        String cloudflareEdge = "172.71.1.1";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);
        request.addHeader("CF-Connecting-IP", REAL_CLIENT);
        request.addHeader("X-Forwarded-For", SPOOFED + ", " + REAL_CLIENT + ", " + cloudflareEdge);

        assertThat(resolver.resolve(request))
                .as("Cloudflare bu başlığı kendisi yazar ve istemcinin gönderdiğini ezer")
                .isEqualTo(REAL_CLIENT);
    }

    @Test
    @DisplayName("başlık yoksa vekilin adresine düşülür")
    void fallsBackToRemoteAddressWithoutHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);

        assertThat(resolver.resolve(request)).isEqualTo(PROXY);
    }

    @Test
    @DisplayName("boş başlık yok sayılır")
    void blankHeaderIsIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(resolver.resolve(request)).isEqualTo(PROXY);
    }

    @Test
    @DisplayName("yapılandırılan ek vekil güvenilir sayılır")
    void configuredProxyIsTrusted() {
        String gateway = "10.0.0.5";
        ClientIpResolver withGateway = new ClientIpResolver(gateway);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(gateway);
        request.addHeader("X-Forwarded-For", REAL_CLIENT);

        assertThat(withGateway.resolve(request)).isEqualTo(REAL_CLIENT);
    }
}
