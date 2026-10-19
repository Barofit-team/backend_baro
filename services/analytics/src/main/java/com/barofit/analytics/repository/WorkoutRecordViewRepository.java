
package com.barofit.analytics.repository;

import com.barofit.analytics.entity.WorkoutRecordView;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutRecordViewRepository
        extends JpaRepository<WorkoutRecordView, UUID> {

    // 사용자별 운동 기록 조회: 완료일 최신순
    Page<WorkoutRecordView> findByUserIdOrderByCompletedAtDesc(
            UUID userId,
            Pageable pageable
    );

    // 특정 사용자의 운동 세션 상세 조회
    Optional<WorkoutRecordView> findBySessionIdAndUserId(
            UUID sessionId,
            UUID userId
    );

    // 운동 세션이 존재하지 않을 때만 기록 저장
    // 동일 session_id가 이미 존재하면 새 기록을 생성하지 않음
    // 반환값: 1 = 신규 저장, 0 = 중복 세션
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO workout_record_views (
                session_id,
                user_id,
                routine_id,
                completed_at,
                duration_seconds,
                total_reps
            )
            VALUES (
                :sessionId,
                :userId,
                :routineId,
                :completedAt,
                :durationSeconds,
                :totalReps
            )
            ON CONFLICT (session_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("routineId") UUID routineId,
            @Param("completedAt") OffsetDateTime completedAt,
            @Param("durationSeconds") int durationSeconds,
            @Param("totalReps") int totalReps
    );
}
