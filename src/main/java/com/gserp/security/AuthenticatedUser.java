package com.gserp.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * Spring Security'nin {@link org.springframework.security.core.userdetails.User} tipini
 * uzatarak DB'deki user id'sini principal üzerinden taşıyabilmesini sağlar. AuditService
 * gibi yerler SecurityContext'ten id'yi alabilir.
 */
@Getter
public class AuthenticatedUser extends User {

    private final Long id;

    public AuthenticatedUser(Long id, String username, String password, boolean enabled,
                              Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.id = id;
    }
}
