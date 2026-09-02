package com.gscrm.tenant;

public final class TenantContext {

    private static final ThreadLocal<Long> salonId = new ThreadLocal<>();
    private static final ThreadLocal<Long> orgId = new ThreadLocal<>();
    private static final ThreadLocal<String> slug = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> platformBypass = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Long getSalonId() {
        return salonId.get();
    }

    public static Long requireSalonId() {
        Long id = salonId.get();
        if (id == null) {
            throw new IllegalStateException("Salon tenant context is not set");
        }
        return id;
    }

    public static Long getOrgId() {
        return orgId.get();
    }

    public static String getSlug() {
        return slug.get();
    }

    public static boolean isPlatformBypass() {
        return Boolean.TRUE.equals(platformBypass.get());
    }

    public static void setSalonId(Long id) {
        salonId.set(id);
    }

    public static void setOrgId(Long id) {
        orgId.set(id);
    }

    public static void setSlug(String value) {
        slug.set(value);
    }

    public static void setPlatformBypass(boolean bypass) {
        platformBypass.set(bypass);
    }

    public static void clear() {
        salonId.remove();
        orgId.remove();
        slug.remove();
        platformBypass.remove();
    }
}
