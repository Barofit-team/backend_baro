package com.barofit.identity.web;

import com.barofit.identity.user.UserAccountService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
class UserController {

	private final UserAccountService userAccountService;

	/**
	 * 탈퇴. 성공하면 앱이 SDK unlink(카카오) 또는 revokeAccess(구글)를 시도한다. 이미 탈퇴했어도 204.
	 */
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(@AuthenticationPrincipal Jwt jwt) {
		userAccountService.withdraw(UUID.fromString(jwt.getSubject()));
	}
}
