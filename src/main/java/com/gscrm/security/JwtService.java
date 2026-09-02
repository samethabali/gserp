package com.gscrm.security;

import com.gscrm.model.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    public static final String CLAIM_TYP = "typ";
    public static final String CLAIM_SALON_ID = "salonId";
    public static final String CLAIM_ORG_ID = "orgId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_STAFF_ID = "staffId";
    public static final String CLAIM_CUSTOMER_ID = "customerId";
    public static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final long accessTtlMillis;
    private final long refreshTtlMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-ttl-minutes:60}") long accessTtlMinutes,
            @Value("${app.jwt.refresh-ttl-days:7}") long refreshTtlDays) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtlMillis = accessTtlMinutes * 60_000L;
        this.refreshTtlMillis = refreshTtlDays * 24L * 60L * 60_000L;
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYP, TYP_ACCESS);
        if (userDetails instanceof AuthenticatedUser au) {
            if (au.getSalonId() != null) claims.put(CLAIM_SALON_ID, au.getSalonId());
            if (au.getOrganizationId() != null) claims.put(CLAIM_ORG_ID, au.getOrganizationId());
            claims.put(CLAIM_ROLE, au.getRole().name());
            if (au.getStaffId() != null) claims.put(CLAIM_STAFF_ID, au.getStaffId());
            if (au.getCustomerId() != null) claims.put(CLAIM_CUSTOMER_ID, au.getCustomerId());
        }
        return buildToken(claims, userDetails.getUsername(), accessTtlMillis);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(Map.of(CLAIM_TYP, TYP_REFRESH), userDetails.getUsername(), refreshTtlMillis);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_TYP, String.class));
    }

    public Long extractSalonId(String token) {
        return extractClaim(token, claims -> {
            Object v = claims.get(CLAIM_SALON_ID);
            if (v instanceof Number n) return n.longValue();
            return null;
        });
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            String typ = extractTokenType(token);
            return username.equals(userDetails.getUsername())
                    && TYP_ACCESS.equals(typ)
                    && !isExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            String typ = extractTokenType(token);
            return username.equals(userDetails.getUsername())
                    && TYP_REFRESH.equals(typ)
                    && !isExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token).getPayload();
        return resolver.apply(claims);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}
