package com.barofit.identity.profile;

import com.barofit.identity.user.User;
import java.util.Optional;

/** 내 정보. 온보딩 전이면 profile이 비어 있다. currentYear는 나이 계산 기준 연도 */
public record MyAccount(User user, Optional<UserProfile> profile, int currentYear) {

	public Optional<Integer> age() {
		return profile.map(p -> p.ageIn(currentYear));
	}
}
