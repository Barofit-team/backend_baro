package com.barofit.workout;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsOutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final AnalyticsOutboxDelivery delivery;

    public AnalyticsOutboxDispatcher(JdbcTemplate jdbc, AnalyticsOutboxDelivery delivery) {
        this.jdbc = jdbc;
        this.delivery = delivery;
    }

    @Scheduled(initialDelay = 1000, fixedDelay = 5000)
    public void dispatch() {
        if (!delivery.configured()) return;
        var pending = jdbc.query("""
            SELECT session_id FROM workout.analytics_completion_outbox
            WHERE delivered_at IS NULL AND next_attempt_at<=now()
            ORDER BY next_attempt_at LIMIT 20
            """, (rs, row) -> (UUID) rs.getObject("session_id"));
        for (UUID sessionId : pending) delivery.deliver(sessionId);
    }
}
