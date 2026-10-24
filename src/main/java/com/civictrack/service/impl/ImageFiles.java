package com.civictrack.service.impl;

import java.util.regex.Pattern;

/** Shared rules for uploaded photos (used by every storage implementation). */
public final class ImageFiles {

    public static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("[a-f0-9\\-]{36}\\.(jpg|png)");

    private ImageFiles() {
    }

    /** Looks at the real file signature ("magic bytes"), not at the name the browser sent. Returns jpg, png or null. */
    public static String detectExtension(byte[] header, int length) {
        if (length >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (length >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return "png";
        }
        return null;
    }

    /** Only names we generated ourselves are accepted (blocks path tricks like ../). */
    public static boolean isSafeName(String filename) {
        return filename != null && SAFE_NAME.matcher(filename).matches();
    }

    public static String contentTypeFor(String extension) {
        return "png".equals(extension) ? "image/png" : "image/jpeg";
    }
}
