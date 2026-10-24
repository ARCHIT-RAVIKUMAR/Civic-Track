package com.civictrack.service;

import com.civictrack.service.impl.CsvUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvUtilTest {

    @Test
    void cellsAreQuotedAndInnerQuotesDoubled() {
        assertEquals("\"say \"\"hi\"\"\"", CsvUtil.cell("say \"hi\""));
    }

    @Test
    void formulaStartsAreNeutralised() {
        assertEquals("\"'=SUM(A1)\"", CsvUtil.cell("=SUM(A1)"));
        assertEquals("\"'+1\"", CsvUtil.cell("+1"));
        assertEquals("\"'-1\"", CsvUtil.cell("-1"));
        assertEquals("\"'@x\"", CsvUtil.cell("@x"));
    }

    @Test
    void nullBecomesEmptyAndRowsEndWithNewline() {
        assertEquals("\"\"", CsvUtil.cell(null));
        assertEquals("\"a\",\"b\"\n", CsvUtil.row("a", "b"));
    }
}
