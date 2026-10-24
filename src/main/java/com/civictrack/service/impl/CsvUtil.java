package com.civictrack.service.impl;

/** Builds safe CSV lines. Cells that start with = + - @ are neutralised so Excel cannot run them as formulas. */
public final class CsvUtil {

    private CsvUtil() {
    }

    public static String cell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    public static String row(String... cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(cell(cells[i]));
        }
        return line.append('\n').toString();
    }
}
