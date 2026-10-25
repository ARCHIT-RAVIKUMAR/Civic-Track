package com.civictrack.repository;

import com.civictrack.entity.Announcement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    @Query("select a from Announcement a where a.active = true and (a.expiresAt is null or a.expiresAt > :now) "
            + "order by a.createdAt desc")
    List<Announcement> findCurrent(@Param("now") LocalDateTime now, Pageable pageable);

    List<Announcement> findAllByOrderByCreatedAtDesc();
}
