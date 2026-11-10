package com.example.bbcinema.repository;

import com.example.bbcinema.model.movie.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long>, JpaSpecificationExecutor<Showtime> {
    boolean existsByAuditoriumIdAndStatusInAndEndTimeAfter(Long auditoriumId,
            java.util.Collection<com.example.bbcinema.enums.ShowtimeStatus> statuses, LocalDateTime now);
    long countByStatusAndStartTimeAfter(com.example.bbcinema.enums.ShowtimeStatus status, LocalDateTime startTime);

    java.util.List<Showtime> findTop6ByStatusAndStartTimeAfterOrderByStartTimeAscIdAsc(
            com.example.bbcinema.enums.ShowtimeStatus status, LocalDateTime startTime);
    @Query("select s.auditorium.id from Showtime s where s.id = :id")
    Optional<Long> findAuditoriumIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Showtime s where s.id = :id")
    Optional<Showtime> findByIdForUpdate(@Param("id") Long id);

    @Query("select (count(s) > 0) from Showtime s where s.auditorium.id = :auditoriumId "
            + "and s.status <> com.example.bbcinema.enums.ShowtimeStatus.CANCELLED "
            + "and s.startTime < :end and s.endTime > :start and s.id <> :excludeId")
    boolean hasOverlap(@Param("auditoriumId") Long auditoriumId, @Param("start") LocalDateTime start,
                       @Param("end") LocalDateTime end, @Param("excludeId") Long excludeId);

    @Override
    Optional<Showtime> findById(Long id);
}
