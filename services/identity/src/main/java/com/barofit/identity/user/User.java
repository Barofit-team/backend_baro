package com.barofit.identity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(schema = "identity", name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	static final int NICKNAME_MAX_LENGTH = 100;
	static final int PROFILE_IMAGE_URL_MAX_LENGTH = 2048;

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private String nickname;

	private String profileImageUrl;

	/** 저장소 객체 key. SNS 외부 URL이면 NULL */
	private String profileImageKey;

	@Enumerated(EnumType.STRING)
	private ProfileImageSource profileImageSource;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status;

	private boolean onboarded;

	private Instant lastLoginAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	private Instant withdrawnAt;

	/** 신규 가입. 닉네임·사진은 가입 때만 SNS 값으로 채운다 */
	public static User register(String nickname, String profileImageUrl, Instant now) {
		User user = new User();
		user.nickname = truncate(nickname, NICKNAME_MAX_LENGTH);
		// 길이를 넘는 URL은 저장하지 않는다
		user.profileImageUrl = profileImageUrl != null && profileImageUrl.length() <= PROFILE_IMAGE_URL_MAX_LENGTH
				? profileImageUrl : null;
		user.profileImageSource = user.profileImageUrl != null ? ProfileImageSource.SOCIAL : null;
		user.role = UserRole.USER;
		user.status = UserStatus.ACTIVE;
		user.onboarded = false;
		user.lastLoginAt = now;
		user.createdAt = now;
		user.updatedAt = now;
		return user;
	}

	public void recordLogin(Instant now) {
		this.lastLoginAt = now;
		this.updatedAt = now;
	}

	/** 온보딩에서 닉네임 확정. 형식 검증은 호출하는 쪽이 한다 */
	public void changeNickname(String nickname, Instant now) {
		this.nickname = nickname;
		this.updatedAt = now;
	}

	public void completeOnboarding(Instant now) {
		this.onboarded = true;
		this.updatedAt = now;
	}

	/** 저장소에 올린 사진으로 바꾼다. @return 이전 저장소 key, 없으면 NULL */
	public String changeProfileImage(ProfileImageSource source, String key, String url, Instant now) {
		String previousKey = this.profileImageKey;
		this.profileImageSource = source;
		this.profileImageKey = key;
		this.profileImageUrl = url;
		this.updatedAt = now;
		return previousKey;
	}

	/** 탈퇴. 행은 남기고 개인정보를 익명화한다 */
	public void withdraw(Instant now) {
		this.nickname = null;
		this.profileImageUrl = null;
		this.profileImageKey = null;
		this.profileImageSource = null;
		this.status = UserStatus.WITHDRAWN;
		this.withdrawnAt = now;
		this.updatedAt = now;
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

	private static String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}
}
