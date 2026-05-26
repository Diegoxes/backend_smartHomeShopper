package com.smarthome.util;

import java.text.Normalizer;
import java.util.Locale;

/** Normalización consistente para alias y fuzzy match (locale-es). */
public final class TextNormalize {

    private TextNormalize() {}

    public static String forMatch(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(Locale.ROOT).trim();
        if (s.isEmpty()) return "";
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
}
