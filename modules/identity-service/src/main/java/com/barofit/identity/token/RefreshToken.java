package com.barofit.identity.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Refresh Token 한 개. 원문은 저장하지 않고 SHA-256 해시만 저장한다.
 * 회전할 때마다 새 행이 생기며, 폐기된 행은 재사용 탐지를 위해 절대 만료까지 남겨 둔다.
 */
@Entity
@Table(schema = "identity", name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false)
	private UUID sessionId;

	@Column(nullable = false, updatable = false)
	private String tokenHash;

	@Column(nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(nullable = false, updatable = false)
	private Instant sessionExpiresAt;

	private Instant revokedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public RefreshToken(UUID userId, UUID sessionId, String tokenHash, Instant expiresAt, Instant sessionExpiresAt,
			Instant now) {
		this.userId = userId;
		this.sessionId = sessionId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.sessionExpiresAt = sessionExpiresAt;
		this.createdAt = now;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}
}
