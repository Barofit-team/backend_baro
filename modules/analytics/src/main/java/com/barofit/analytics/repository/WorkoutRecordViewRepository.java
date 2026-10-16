
package com.barofit.analytics.repository;

import com.barofit.analytics.entity.WorkoutRecordView;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
