package com.civictrack.entity;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reference numbers look like CT-7K3M-9QXA. They are random (not sequential) so nobody can guess
 * other people's complaints, and they avoid look-alike characters (0/O, 1/I/L).
 */
public final class ReferenceNumbers {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EIGHT_CHARS = Pattern.compile("[A-Z0-9]{8}");

    private ReferenceNumbers() {
    }

    public static String generate() {
        StringBuilder code = new StringBuilder("CT-");
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Accepts "ct-ab12-cd34", "CTAB12CD34", " ab12 cd34 " ...; returns the canonical form or null if invalid. */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = input.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (cleaned.length() == 10 && cleaned.startsWith("CT")) {
            cleaned = cleaned.substring(2);
        }
        if (!EIGHT_CHARS.matcher(cleaned).matches()) {
            return null;
        }
        return "CT-" + cleaned.substring(0, 4) + "-" + cleaned.substring(4);
    }
}
