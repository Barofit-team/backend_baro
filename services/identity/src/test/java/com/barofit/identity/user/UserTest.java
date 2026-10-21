package com.barofit.identity.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

	private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");

	@Test
	void 신규_가입자는_ACTIVE_USER이고_온보딩_전이다() {
		User user = User.register("닉네임", "https://example.com/a.png", NOW);

		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		assertThat(user.isOnboarded()).isFalse();
		assertThat(user.getLastLoginAt()).isEqualTo(NOW);
	}

	@Test
	void 긴_닉네임은_잘라서_저장하고_긴_URL은_저장하지_않는다() {
		User user = User.register("가".repeat(150), "https://example.com/" + "a".repeat(3000), NOW);

		assertThat(user.getNickname()).hasSize(User.NICKNAME_MAX_LENGTH);
		assertThat(user.getProfileImageUrl()).isNull();
	}

	@Test
	void 탈퇴하면_개인정보를_익명화한다() {
		User user = User.register("닉네임", "https://example.com/a.png", NOW);

		user.withdraw(NOW.plusSeconds(60));

		assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
		assertThat(user.getNickname()).isNull();
		assertThat(user.getProfileImageUrl()).isNull();
		assertThat(user.getWithdrawnAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	void 닉네임을_바꾸고_온보딩을_완료한다() {
		User user = User.register("카카오닉", null, NOW);

		user.changeNickname("바로핏", NOW.plusSeconds(60));
		user.completeOnboarding(NOW.plusSeconds(60));

		assertThat(user.getNickname()).isEqualTo("바로핏");
		assertThat(user.isOnboarded()).isTrue();
		assertThat(user.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
	}
}
