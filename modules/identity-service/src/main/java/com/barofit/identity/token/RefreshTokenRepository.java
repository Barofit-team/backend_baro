package com.barofit.identity.token;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** 유효한 토큰만 폐기. 1행이면 회전 성공 */
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

	/** 절대 만료가 지난 행만 삭제(재사용 탐지 유지) */
	@Modifying
	@Query("delete from RefreshToken t where t.sessionExpiresAt < :now")
	int deleteSessionExpiredBefore(Instant now);
}
