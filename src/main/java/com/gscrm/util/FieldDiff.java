package com.gscrm.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bir güncellemede neyin değiştiğini "eski → yeni" olarak toplar.
 *
 * <p>Kütükte "ayse randevuyu güncelledi" satırı tek başına işe yaramıyordu: fiyatın mı
 * saatin mi değiştiği görünmüyordu. Fark burada toplanıp {@code activity_event.detail}
 * alanına JSON olarak yazılıyor.
 *
 * <p>JSON elle üretiliyor çünkü değerler zaten ilkel tiplere çevrilmiş kısa metinler;
 * araya bir serileştirici koymak, log yazımının iş akışını bozabilecek bir hata
 * kaynağı eklemek olurdu.
 */
public final class FieldDiff {

    private final Map<String, String[]> changes = new LinkedHashMap<>();

    private FieldDiff() {
    }

    public static FieldDiff create() {
        return new FieldDiff();
    }

    /** Değer değiştiyse farkı kaydeder; aynıysa hiçbir şey yapmaz. */
    public FieldDiff compare(String field, Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return this;
        }
        changes.put(field, new String[]{stringify(oldValue), stringify(newValue)});
        return this;
    }

    /** Telefon/e-posta gibi kişisel veriler kütüğe açık yazılmaz. */
    public FieldDiff compareMasked(String field, Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return this;
        }
        changes.put(field, new String[]{mask(stringify(oldValue)), mask(stringify(newValue))});
        return this;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /** Değişiklik yoksa null döner — {@code detail} alanı boş kalsın diye. */
    public String toJson() {
        if (changes.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String[]> entry : changes.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(entry.getKey())).append(":{\"eski\":").append(quote(entry.getValue()[0]))
              .append(",\"yeni\":").append(quote(entry.getValue()[1])).append('}');
        }
        return sb.append('}').toString();
    }

    private static String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return value == null ? null : "***";
        }
        return "***" + value.substring(value.length() - 4);
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
