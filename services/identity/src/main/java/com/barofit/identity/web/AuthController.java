package com.barofit.identity.web;

import com.barofit.common.error.BusinessException;
import com.barofit.identity.auth.AuthService;
import com.barofit.identity.auth.RefreshResult;
import com.barofit.identity.web.AuthDtos.LogoutRequest;
import com.barofit.identity.web.AuthDtos.RefreshRequest;
import com.barofit.identity.web.AuthDtos.SocialCodeLoginRequest;
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

/** 인증 API(공개 경로) */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

	private final AuthService authService;

	@PostMapping("/social-login")
	SocialLoginResponse socialLogin(@Valid @RequestBody SocialLoginRequest request) {
		return SocialLoginResponse.from(authService.socialLogin(request.provider(), request.idToken()));
	}

	/** 인가 코드 로그인. 응답은 social-login과 같다 */
	@PostMapping("/social-login/code")
	SocialLoginResponse socialLoginWithCode(@Valid @RequestBody SocialCodeLoginRequest request) {
		return SocialLoginResponse.from(authService.socialLoginWithCode(
				request.provider(), request.code(), request.redirectUri(), request.nonce()));
	}

	@PostMapping("/refresh")
	TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		// 커밋 후 예외로 변환(세션 폐기 롤백 방지)
		return switch (authService.refresh(request.refreshToken())) {
			case RefreshResult.Success success -> TokenResponse.from(success.tokens());
			case RefreshResult.Failure failure -> throw new BusinessException(failure.errorCode());
		};
	}

	/** 토큰 상태와 상관없이 항상 204 */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
	}
}
