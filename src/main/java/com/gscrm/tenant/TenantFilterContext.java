package com.gscrm.tenant;

import java.util.function.Supplier;

/**
 * Hibernate tenant filtresinin bu iş parçacığı için kapatıldığını işaretler.
 *
 * <p>Varsayılan davranış <b>filtre açık</b>tır: {@link TenantContext} bir salon
 * taşıyorsa, tenant entity'lerine yapılan her sorgu otomatik olarak o salona
 * kısıtlanır. Organizasyon geneli okuma yapması gereken dar sayıda yol (org
 * panosu, zamanlanmış işler, provisioning) bu kapıdan açıkça geçmek zorundadır.
 *
 * <p>Kaçışların <em>sayılabilir</em> kalması bu tasarımın can damarıdır: kodda
 * {@code runUnfiltered} aramak, izolasyondan muaf tüm yolları listeler. Bu yüzden
 * {@code session.disableFilter} doğrudan çağrılmamalıdır.
 */
public final class TenantFilterContext {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private TenantFilterContext() {
    }

    /** Filtre şu an bu iş parçacığı için devre dışı mı? */
    public static boolean isUnfiltered() {
        Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }

    /** Verilen işi tenant filtresi kapalıyken çalıştırır (değer döndüren biçim). */
    public static <T> T runUnfiltered(Supplier<T> action) {
        enter();
        try {
            return action.get();
        } finally {
            exit();
        }
    }

    /** Verilen işi tenant filtresi kapalıyken çalıştırır. */
    public static void runUnfiltered(Runnable action) {
        enter();
        try {
            action.run();
        } finally {
            exit();
        }
    }

    private static void enter() {
        Integer depth = DEPTH.get();
        DEPTH.set(depth == null ? 1 : depth + 1);
    }

    private static void exit() {
        Integer depth = DEPTH.get();
        if (depth == null || depth <= 1) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth - 1);
        }
    }
}
