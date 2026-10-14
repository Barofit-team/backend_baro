package com.barofit.identity.web;

import com.barofit.identity.auth.LoginResult;
import com.barofit.identity.token.IssuedTokens;
import com.barofit.identity.user.SocialProvider;
import com.barofit.identity.user.User;
import com.barofit.identity.user.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

final class AuthDtos {

	private AuthDtos() {
	}

	record SocialLoginRequest(@NotNull SocialProvider provider, @NotBlank String idToken) {
	}

	/** redirectUri·nonce는 로그인 요청에 쓴 값 */
	record SocialCodeLoginRequest(
			@NotNull SocialProvider provider,
			@NotBlank String code,
			@NotBlank String redirectUri,
			@NotBlank String nonce) {
	}

	record RefreshRequest(@NotBlank String refreshToken) {
	}

	record LogoutRequest(@NotBlank String refreshToken) {
	}

	record SocialLoginResponse(
			String accessToken,
			Instant accessTokenExpiresAt,
			String refreshToken,
			boolean isOnboarded,
			UserResponse user) {

		static SocialLoginResponse from(LoginResult result) {
			IssuedTokens tokens = result.tokens();
			User user = result.user();
			return new SocialLoginResponse(tokens.accessToken(), tokens.accessTokenExpiresAt(), tokens.refreshToken(),
					user.isOnboarded(), UserResponse.from(user));
		}
	}

	record TokenResponse(String accessToken, Instant accessTokenExpiresAt, String refreshToken) {

		static TokenResponse from(IssuedTokens tokens) {
			return new TokenResponse(tokens.accessToken(), tokens.accessTokenExpiresAt(), tokens.refreshToken());
		}
	}

	record UserResponse(UUID id, String nickname, String profileImageUrl, UserRole role) {

		static UserResponse from(User user) {
			return new UserResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), user.getRole());
		}
	}
}
