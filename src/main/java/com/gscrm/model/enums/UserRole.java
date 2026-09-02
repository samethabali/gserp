package com.gscrm.model.enums;

/**
 * Platform and salon-scoped roles. ADMIN kept for backward compatibility (= BRANCH_MANAGER).
 */
public enum UserRole {
    PLATFORM_ADMIN,
    ORG_OWNER,
    BRANCH_MANAGER,
    ADMIN,
    RECEPTIONIST,
    SPECIALIST,
    CUSTOMER;

    public boolean isStaffRole() {
        return this != CUSTOMER && this != PLATFORM_ADMIN;
    }

    public boolean isManagementRole() {
        return this == PLATFORM_ADMIN || this == ORG_OWNER || this == BRANCH_MANAGER || this == ADMIN;
    }
}
