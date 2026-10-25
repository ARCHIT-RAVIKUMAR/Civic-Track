package com.civictrack.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReferenceNumbersTest {

    @Test
    void generatedNumbersHaveTheExpectedShapeAndAreDifferent() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String ref = ReferenceNumbers.generate();
            assertTrue(ref.matches("CT-[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}"), ref);
            seen.add(ref);
        }
        assertEquals(2000, seen.size());
    }

    @Test
    void normalizeAcceptsCommonWaysOfTypingAReference() {
        assertEquals("CT-AB12-CD34", ReferenceNumbers.normalize("CT-AB12-CD34"));
        assertEquals("CT-AB12-CD34", ReferenceNumbers.normalize("  ct-ab12-cd34 "));
        assertEquals("CT-AB12-CD34", ReferenceNumbers.normalize("CTAB12CD34"));
        assertEquals("CT-AB12-CD34", ReferenceNumbers.normalize("ab12 cd34"));
    }

    @Test
    void codeThatItselfStartsWithCtIsNotMistakenForThePrefix() {
        assertEquals("CT-CT12-3456", ReferenceNumbers.normalize("CT-CT12-3456"));
        assertEquals("CT-CT12-3456", ReferenceNumbers.normalize("CT123456"));
    }

    @Test
    void normalizeRejectsNonsense() {
        assertNull(ReferenceNumbers.normalize(null));
        assertNull(ReferenceNumbers.normalize(""));
        assertNull(ReferenceNumbers.normalize("hello"));
        assertNull(ReferenceNumbers.normalize("CT-12"));
        assertNull(ReferenceNumbers.normalize("CT-AB12-CD34-EF56"));
    }
}
