package net.juli2kapo.factoryascent.storagenet;

import java.util.Locale;

/** Short number formatting for the storage GUIs: 999, 1.2k, 15k, 3.4M. */
public final class StorageFormat {
    private StorageFormat() {}

    public static String compact(long n) {
        if (n < 1_000) return Long.toString(n);
        if (n < 10_000) return trim(String.format(Locale.ROOT, "%.1f", Math.floor(n / 100.0) / 10.0)) + "k";
        if (n < 1_000_000) return (n / 1_000) + "k";
        if (n < 10_000_000) return trim(String.format(Locale.ROOT, "%.1f", Math.floor(n / 100_000.0) / 10.0)) + "M";
        if (n < 1_000_000_000) return (n / 1_000_000) + "M";
        return (n / 1_000_000_000) + "G";
    }

    private static String trim(String s) {
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
