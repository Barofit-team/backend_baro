package com.barofit.identity.profile;

import com.barofit.identity.user.User;
import java.util.Optional;

/** 내 정보. 온보딩 전이면 profile이 비어 있다 */
public record MyAccount(User user, Optional<UserProfile> profile) {
}
