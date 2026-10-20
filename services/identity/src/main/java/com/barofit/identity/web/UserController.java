package com.barofit.identity.web;

import com.barofit.identity.profile.NicknameService;
import com.barofit.identity.profile.ProfileService;
import com.barofit.identity.user.UserAccountService;
import com.barofit.identity.web.UserDtos.MeResponse;
import com.barofit.identity.web.UserDtos.NicknameAvailabilityResponse;
import com.barofit.identity.web.UserDtos.NicknameResponse;
import com.barofit.identity.web.UserDtos.OnboardingRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
class UserController {

	private final UserAccountService userAccountService;
	private final ProfileService profileService;
	private final NicknameService nicknameService;

	@GetMapping("/me")
	MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		return MeResponse.from(profileService.getMyAccount(userId(jwt)));
	}

	/** 온보딩 저장. 한 번만 할 수 있다 */
	@PostMapping("/me/onboarding")
	@ResponseStatus(HttpStatus.CREATED)
	MeResponse onboard(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OnboardingRequest request) {
		return MeResponse.from(profileService.onboard(userId(jwt), request.toCommand()));
	}

	/** 사용 중이지 않은 랜덤 닉네임. 예약하지 않는다 */
	@GetMapping("/nicknames/random")
	NicknameResponse randomNickname() {
		return new NicknameResponse(nicknameService.generate());
	}

	@GetMapping("/nicknames/availability")
	NicknameAvailabilityResponse nicknameAvailability(@RequestParam String nickname) {
		return NicknameAvailabilityResponse.of(nickname, nicknameService.check(nickname));
	}

	/** 탈퇴. 이미 탈퇴했어도 204 */
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void withdraw(@AuthenticationPrincipal Jwt jwt) {
		userAccountService.withdraw(userId(jwt));
	}

	private static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
