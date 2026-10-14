package com.barofit.identity.token;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * 회전의 원자성: 유효한 토큰일 때만 폐기한다. 영향 행 수가 1이면 이 요청이 회전에 성공한 것이다.
	 * 같은 토큰으로 동시에 들어온 요청은 행 잠금을 기다린 뒤 조건을 다시 평가해 0행이 된다.
	 */
	@Modifying(flushAutomatically = true)
	@Query("""
			update RefreshToken t set t.revokedAt = :now
			where t.tokenHash = :tokenHash and t.revokedAt is null and t.expiresAt > :now
			""")
	int revokeIfActive(String tokenHash, Instant now);

	@Modifying(flushAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now where t.sessionId = :sessionId and t.revokedAt is null")
	int revokeSession(UUID sessionId, Instant now);

	@Modifying(flushAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
	int revokeAllByUserId(UUID userId, Instant now);

	/**
	 * 절대 만료가 지난 행만 지운다. 폐기된 행을 바로 지우면 재사용 탐지가 동작하지 않는다.
	 */
	@Modifying
	@Query("delete from RefreshToken t where t.sessionExpiresAt < :now")
	int deleteSessionExpiredBefore(Instant now);
}
