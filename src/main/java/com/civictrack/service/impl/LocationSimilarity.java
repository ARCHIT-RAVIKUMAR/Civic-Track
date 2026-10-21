package com.civictrack.service.impl;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Simple text comparison used to warn about possible duplicate complaints. */
public final class LocationSimilarity {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "near", "opposite", "behind", "road", "street", "lane", "cross", "main",
            "area", "next", "beside", "side", "and", "of", "in", "at", "to", "on");

    private LocationSimilarity() {
    }

    public static boolean isSimilar(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        if (x.equals(y)) {
            return true;
        }
        String shorter = x.length() <= y.length() ? x : y;
        String longer = x.length() <= y.length() ? y : x;
        if (shorter.length() >= 4 && longer.contains(shorter)) {
            return true;
        }
        Set<String> tokensX = tokens(x);
        Set<String> tokensY = tokens(y);
        if (tokensX.isEmpty() || tokensY.isEmpty()) {
            return false;
        }
        Set<String> common = new HashSet<>(tokensX);
        common.retainAll(tokensY);
        Set<String> union = new HashSet<>(tokensX);
        union.addAll(tokensY);
        return !common.isEmpty() && ((double) common.size() / union.size()) >= 0.5;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static Set<String> tokens(String normalized) {
        Set<String> result = new HashSet<>();
        for (String token : normalized.split(" ")) {
            if (token.length() >= 2 && !STOP_WORDS.contains(token)) {
                result.add(token);
            }
        }
        return result;
    }
}
