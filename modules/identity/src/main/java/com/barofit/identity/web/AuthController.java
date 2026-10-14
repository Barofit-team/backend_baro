package com.barofit.identity.web;

import com.barofit.common.error.BusinessException;
import com.barofit.identity.auth.AuthService;
import com.barofit.identity.auth.RefreshResult;
import com.barofit.identity.web.AuthDtos.LogoutRequest;
import com.barofit.identity.web.AuthDtos.RefreshRequest;
import com.barofit.identity.web.AuthDtos.SocialLoginRequest;
import com.barofit.identity.web.AuthDtos.SocialLoginResponse;
import com.barofit.identity.web.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. 모두 공개 경로(/api/v1/auth/**)이며 Access Token 없이 호출한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

	private final AuthService authService;

	@PostMapping("/social-login")
	SocialLoginResponse socialLogin(@Valid @RequestBody SocialLoginRequest request) {
		return SocialLoginResponse.from(authService.socialLogin(request.provider(), request.idToken()));
	}

	@PostMapping("/refresh")
	TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		// 서비스 트랜잭션이 커밋된 뒤에 예외로 바꾼다(재사용 탐지의 세션 폐기가 롤백되지 않도록)
		return switch (authService.refresh(request.refreshToken())) {
			case RefreshResult.Success success -> TokenResponse.from(success.tokens());
			case RefreshResult.Failure failure -> throw new BusinessException(failure.errorCode());
		};
	}

	/** body의 Refresh Token으로 세션을 식별한다. 토큰 상태와 상관없이 항상 204 */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
	}
}
