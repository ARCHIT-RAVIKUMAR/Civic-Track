package com.civictrack.service;

import com.civictrack.service.impl.LocationSimilarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationSimilarityTest {

    @Test
    void sameLocationIgnoringCaseAndPunctuation() {
        assertTrue(LocationSimilarity.isSimilar("MG Road, Bus Stop", "mg road bus stop"));
    }

    @Test
    void oneLocationContainedInTheOther() {
        assertTrue(LocationSimilarity.isSimilar("MG Road near the bus stop", "mg road"));
    }

    @Test
    void sameWordsInDifferentOrder() {
        assertTrue(LocationSimilarity.isSimilar("Anna Nagar 2nd Street", "2nd street anna nagar"));
    }

    @Test
    void differentPlacesAreNotSimilar() {
        assertFalse(LocationSimilarity.isSimilar("Park Avenue", "Gandhi Nagar bus stand"));
    }

    @Test
    void blankOrNullNeverMatches() {
        assertFalse(LocationSimilarity.isSimilar("", "MG Road"));
        assertFalse(LocationSimilarity.isSimilar(null, null));
    }
}
