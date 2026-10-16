
package com.barofit.analytics.dto;

public record StatisticsSummaryResponse(
        Integer sessionCount,
        Integer totalDurationSeconds,
        Integer totalReps
) {
}
