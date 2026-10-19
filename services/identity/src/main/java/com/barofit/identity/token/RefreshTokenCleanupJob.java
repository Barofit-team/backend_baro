package com.barofit.identity.token;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 절대 만료가 지난 Refresh Token 삭제(멱등) */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

	private final RefreshTokenRepository refreshTokenRepository;
	private final Clock clock;

	@Scheduled(cron = "${barofit.refresh-token.cleanup-cron:0 0 4 * * *}")
	@Transactional
	public void deleteSessionExpired() {
		int deleted = refreshTokenRepository.deleteSessionExpiredBefore(clock.instant());
		log.info("Deleted {} session-expired refresh tokens", deleted);
	}
}
