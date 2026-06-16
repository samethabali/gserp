package com.gserp.security;

import com.gserp.model.enums.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

@Getter
public class AuthenticatedUser extends User {

    private final Long id;
    private final UserRole role;
    private final Long staffId;
    private final Long customerId;
    private final Long salonId;
    private final Long organizationId;
    private final boolean mustChangePassword;

    public AuthenticatedUser(Long id, String username, String password, boolean enabled,
                             UserRole role, Long staffId, Long customerId,
                             Long salonId, Long organizationId, boolean mustChangePassword,
                             Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.id = id;
        this.role = role;
        this.staffId = staffId;
        this.customerId = customerId;
        this.salonId = salonId;
        this.organizationId = organizationId;
        this.mustChangePassword = mustChangePassword;
    }
}
