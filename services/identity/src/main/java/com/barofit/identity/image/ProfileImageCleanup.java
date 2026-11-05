package com.barofit.identity.image;

import com.barofit.identity.user.ProfileImageDiscarded;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 사진 교체·탈퇴가 커밋된 뒤 이전 사진을 저장소에서 지운다. 실패해도 요청은 성공으로 둔다 */
@Component
@RequiredArgsConstructor
class ProfileImageCleanup {

	private final ProfileImageService profileImageService;

	@TransactionalEventListener
	void on(ProfileImageDiscarded event) {
		profileImageService.deleteQuietly(event.key());
	}
}
