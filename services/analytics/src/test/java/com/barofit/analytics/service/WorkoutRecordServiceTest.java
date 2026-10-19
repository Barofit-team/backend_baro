
package com.barofit.analytics.service;

import com.barofit.analytics.dto.WorkoutRecordResponse;
import com.barofit.analytics.entity.WorkoutRecordView;
import com.barofit.analytics.repository.WorkoutRecordViewRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkoutRecordServiceTest {

    private WorkoutRecordViewRepository repository;
    private WorkoutRecordService service;

    private UUID userId;
    private UUID sessionId;
    private WorkoutRecordView record;

    @BeforeEach
    void setUp() {
        repository = mock(WorkoutRecordViewRepository.class);
        service = new WorkoutRecordService(repository);

        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        record = new WorkoutRecordView(
                sessionId,
                userId,
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-10-15T10:00:00+09:00"),
                1800,
                120
        );
    }

    @Test
    void getWorkoutRecords_returnsPagedRecords() {
        var pageable = PageRequest.of(0, 10);
        var records = new PageImpl<>(
                List.of(record),
                pageable,
                1
        );

        when(repository.findByUserIdOrderByCompletedAtDesc(
                userId, pageable
        )).thenReturn(records);

        Page<WorkoutRecordResponse> result =
                service.getWorkoutRecords(userId, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(sessionId, result.getContent().get(0).sessionId());
        assertEquals(1800, result.getContent().get(0).durationSeconds());

        verify(repository).findByUserIdOrderByCompletedAtDesc(
                userId, pageable
        );
    }

    @Test
    void getWorkoutRecord_returnsMatchingSession() {
        when(repository.findBySessionIdAndUserId(
                sessionId, userId
        )).thenReturn(Optional.of(record));

        Optional<WorkoutRecordResponse> result =
                service.getWorkoutRecord(userId, sessionId);

        assertTrue(result.isPresent());
        assertEquals(sessionId, result.orElseThrow().sessionId());
        assertEquals(120, result.orElseThrow().totalReps());
    }

    @Test
    void getWorkoutRecord_returnsEmptyWhenNotFound() {
        when(repository.findBySessionIdAndUserId(
                sessionId, userId
        )).thenReturn(Optional.empty());

        Optional<WorkoutRecordResponse> result =
                service.getWorkoutRecord(userId, sessionId);

        assertTrue(result.isEmpty());
    }
}
