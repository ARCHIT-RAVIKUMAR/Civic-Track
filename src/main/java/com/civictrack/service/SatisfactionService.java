package com.civictrack.service;

public interface SatisfactionService {
    /** Aggregated, anonymous rating statistics (no names or comments). */
    SatisfactionSummary summary();
}
