package com.gscrm.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * İstemci IP'sini <b>tek</b> yerde çözer.
 *
 * <p>{@code X-Forwarded-For} başlığına yalnızca istek güvenilen bir vekilden
 * geldiyse itibar edilir; aksi hâlde başlık istemci kontrolündedir ve saldırgan
 * her istekte farklı bir sahte IP göndererek her türlü sayacı atlar. Bu mantığın
 * ikinci bir kopyasının olması, tam da spoofing hatalarının doğduğu yerdir —
 * hız sınırı ve randevu kötüye kullanım sayacı aynı çözümleyiciyi kullanır.
 */
@Component
public class ClientIpResolver {

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    private final List<String> trustedProxies;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    /**
     * Vekil güvenilir mi? Uygulama loopback'e bağlanıp nginx arkasında çalıştığı için
     * loopback varsayılan olarak güvenilirdir; ek vekiller
     * {@code app.security.trusted-proxies} ile tanımlanır.
     */
    public boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        if (LOOPBACK.contains(remoteAddr) || trustedProxies.contains(remoteAddr)) {
            return true;
        }
        try {
            return InetAddress.getByName(remoteAddr).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
