package com.barofit.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barofit.IntegrationTest;
import com.barofit.identity.token.RefreshToken;
import com.barofit.identity.token.RefreshTokenRepository;
import com.barofit.identity.user.SocialAccount;
import com.barofit.identity.user.SocialAccountRepository;
import com.barofit.identity.user.SocialProvider;
import com.barofit.identity.user.User;
import com.barofit.identity.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class IdentityPersistenceTest {

	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

	@Autowired
	UserRepository userRepository;

	@Autowired
	SocialAccountRepository socialAccountRepository;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 사용자_ID는_UUIDv7로_생성된다() {
		User user = userRepository.saveAndFlush(User.register("nick", null, NOW));

		assertThat(user.getId().version()).isEqualTo(7);
	}

	@Test
	void 같은_provider와_providerUserId로_두_번_가입할_수_없다() {
		User first = userRepository.saveAndFlush(User.register("a", null, NOW));
		User second = userRepository.saveAndFlush(User.register("b", null, NOW));
		socialAccountRepository.saveAndFlush(new SocialAccount(first.getId(), SocialProvider.KAKAO, "123", NOW));

		assertThatThrownBy(() -> socialAccountRepository.saveAndFlush(
				new SocialAccount(second.getId(), SocialProvider.KAKAO, "123", NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void status는_허용된_값만_저장된다() {
		User user = userRepository.saveAndFlush(User.register("nick", null, NOW));

		assertThatThrownBy(() -> jdbcTemplate.update(
				"update identity.users set status = 'BLOCKED' where id = ?", user.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 유효한_토큰만_한_번_폐기된다() {
		User user = userRepository.saveAndFlush(User.register("nick", null, NOW));
		refreshTokenRepository.saveAndFlush(token(user, UUID.randomUUID(), "hash-1", NOW.plus(Duration.ofDays(30))));

		assertThat(refreshTokenRepository.revokeIfActive("hash-1", NOW)).isEqualTo(1);
		assertThat(refreshTokenRepository.revokeIfActive("hash-1", NOW)).isZero();
	}

	@Test
	void 만료된_토큰은_폐기되지_않는다() {
		User user = userRepository.saveAndFlush(User.register("nick", null, NOW));
		refreshTokenRepository.saveAndFlush(token(user, UUID.randomUUID(), "hash-expired", NOW.minusSeconds(1)));

		assertThat(refreshTokenRepository.revokeIfActive("hash-expired", NOW)).isZero();
	}

	@Test
	void 세션_단위로_폐기된다() {
		User user = userRepository.saveAndFlush(User.register("nick", null, NOW));
		UUID session = UUID.randomUUID();
		UUID otherSession = UUID.randomUUID();
		refreshTokenRepository.saveAndFlush(token(user, session, "s1", NOW.plus(Duration.ofDays(30))));
		refreshTokenRepository.saveAndFlush(token(user, session, "s2", NOW.plus(Duration.ofDays(30))));
		refreshTokenRepository.saveAndFlush(token(user, otherSession, "o1", NOW.plus(Duration.ofDays(30))));

		assertThat(refreshTokenRepository.revokeSession(session, NOW)).isEqualTo(2);
		assertThat(refreshTokenRepository.revokeIfActive("o1", NOW)).isEqualTo(1);
	}

	@Test
	void 정리_배치는_절대_만료가_지난_행만_지운다() {
		User user = userRepository.saveAndFlush(User.register("nick", null, NOW));
		RefreshToken expired = new RefreshToken(user.getId(), UUID.randomUUID(), "old", NOW.minus(Duration.ofDays(60)),
				NOW.minusSeconds(1), NOW.minus(Duration.ofDays(90)));
		refreshTokenRepository.saveAndFlush(expired);
		refreshTokenRepository.saveAndFlush(token(user, UUID.randomUUID(), "revoked", NOW.plus(Duration.ofDays(30))));
		refreshTokenRepository.revokeIfActive("revoked", NOW);

		assertThat(refreshTokenRepository.deleteSessionExpiredBefore(NOW)).isEqualTo(1);
		assertThat(refreshTokenRepository.findByTokenHash("revoked")).isPresent();
	}

	private RefreshToken token(User user, UUID sessionId, String hash, Instant expiresAt) {
		return new RefreshToken(user.getId(), sessionId, hash, expiresAt, NOW.plus(Duration.ofDays(90)), NOW);
	}
}
