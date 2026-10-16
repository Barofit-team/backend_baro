
package com.barofit.analytics.service;

import com.barofit.analytics.dto.WorkoutRecordResponse;
import com.barofit.analytics.repository.WorkoutRecordViewRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WorkoutRecordService {

    private final WorkoutRecordViewRepository repository;

    public WorkoutRecordService(WorkoutRecordViewRepository repository) {
        this.repository = repository;
    }

    public Page<WorkoutRecordResponse> getWorkoutRecords(
            UUID userId,
            Pageable pageable
    ) {
        return repository
                .findByUserIdOrderByCompletedAtDesc(userId, pageable)
                .map(WorkoutRecordResponse::from);
    }

    public Optional<WorkoutRecordResponse> getWorkoutRecord(
            UUID userId,
            UUID sessionId
    ) {
        return repository
                .findBySessionIdAndUserId(sessionId, userId)
                .map(WorkoutRecordResponse::from);
    }
}
