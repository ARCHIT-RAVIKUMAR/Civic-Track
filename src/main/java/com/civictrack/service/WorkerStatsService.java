package com.civictrack.service;

import com.civictrack.entity.User;

import java.util.List;

public interface WorkerStatsService {
    /** One row per field worker. Allowed for authorities and administrators. */
    List<WorkerStats> stats(User actor);
}
