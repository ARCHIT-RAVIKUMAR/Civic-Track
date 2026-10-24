package com.civictrack.repository;

import com.civictrack.entity.ActivityLog;
import com.civictrack.entity.User;
import com.civictrack.enums.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    List<ActivityLog> findTop200ByUserOrderByCreatedAtDesc(User user);

    Optional<ActivityLog> findFirstByUserAndTypeOrderByCreatedAtDesc(User user, ActivityType type);

    void deleteByUser(User user);

    @Modifying
    @Query("update ActivityLog a set a.actor = null where a.actor = :user")
    void clearActor(@Param("user") User user);
}
