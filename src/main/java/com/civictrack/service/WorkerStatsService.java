package com.civictrack.service;

import com.civictrack.entity.User;

import java.util.List;

public interface WorkerStatsService {
    /** One row per field worker. Allowed for authorities and administrators. */
    List<WorkerStats> stats(User actor);

    /** A field worker's own numbers, including the latest citizen comments on their jobs. */
    WorkerStats myStats(User worker);
}
