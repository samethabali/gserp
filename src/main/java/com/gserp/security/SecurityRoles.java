package com.gserp.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Spring Security role helpers for multi-tenant RBAC.
 */
public final class SecurityRoles {

    public static final String MANAGEMENT = "BRANCH_MANAGER,ORG_OWNER,PLATFORM_ADMIN,ADMIN";
    public static final String STAFF = "RECEPTIONIST,SPECIALIST";
    public static final String MANAGEMENT_AND_STAFF = MANAGEMENT + "," + STAFF;

    private SecurityRoles() {}

    public static String[] managementRoles() {
        return new String[]{"BRANCH_MANAGER", "ORG_OWNER", "PLATFORM_ADMIN", "ADMIN"};
    }

    public static String[] managementAndReceptionist() {
        return new String[]{"BRANCH_MANAGER", "ORG_OWNER", "PLATFORM_ADMIN", "ADMIN", "RECEPTIONIST"};
    }

    public static String[] allStaffExceptCustomer() {
        return new String[]{"PLATFORM_ADMIN", "ORG_OWNER", "BRANCH_MANAGER", "ADMIN", "RECEPTIONIST", "SPECIALIST"};
    }
}
