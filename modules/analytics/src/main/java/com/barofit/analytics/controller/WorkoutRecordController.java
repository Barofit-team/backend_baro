
package com.barofit.analytics.controller;

import com.barofit.analytics.service.WorkoutRecordService;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workout-records")
public class WorkoutRecordController {

    private final WorkoutRecordService service;

    public WorkoutRecordController(WorkoutRecordService service) {
        this.service = service;
    }

    // TODO: 인증 연동 후 운동 기록 목록 조회 API 구현

    // TODO: 인증 연동 후 운동 기록 상세 조회 API 구현
}
