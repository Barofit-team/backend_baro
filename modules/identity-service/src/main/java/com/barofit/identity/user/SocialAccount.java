package com.barofit.identity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원은 (provider, providerUserId)로 식별한다 */
@Entity
@Table(schema = "identity", name = "social_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private SocialProvider provider;

	@Column(nullable = false, updatable = false)
	private String providerUserId;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public SocialAccount(UUID userId, SocialProvider provider, String providerUserId, Instant now) {
		this.userId = userId;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.createdAt = now;
	}
}
