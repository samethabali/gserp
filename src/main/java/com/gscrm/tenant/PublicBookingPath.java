package com.gscrm.tenant;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kök seviyedeki herkese açık işletme adreslerini tanır. */
public final class PublicBookingPath {

    private static final Pattern ROOT_PATH =
            Pattern.compile("^/([a-z0-9][a-z0-9-]{1,62})/?$");
    private static final Pattern LEGACY_PATH =
            Pattern.compile("^/b/([a-z0-9][a-z0-9-]{1,62})(/.*)?$");

    private static final Set<String> RESERVED_SLUGS = Set.of(
            "www", "api", "app", "platform", "admin", "static", "health", "actuator",
            "gserp", "gscrm", "b", "s", "login", "logout", "error",
            "onboarding", "customer", "org", "settings", "booking", "privacy",
            "dashboard", "services", "staff", "resources", "audit", "customers",
            "expenses", "products", "campaigns", "users", "change-password",
            "css", "js", "images", "webjars", "favicon.ico");

    private PublicBookingPath() {
    }

    public static String extractSlug(String path) {
        Matcher legacy = LEGACY_PATH.matcher(path);
        if (legacy.matches()) {
            return legacy.group(1);
        }
        Matcher root = ROOT_PATH.matcher(path);
        if (!root.matches() || isReserved(root.group(1))) {
            return null;
        }
        return root.group(1);
    }

    public static boolean isPublicRootPath(String path) {
        Matcher root = ROOT_PATH.matcher(path);
        return root.matches() && !isReserved(root.group(1));
    }

    public static boolean isReserved(String slug) {
        return RESERVED_SLUGS.contains(slug);
    }
}
