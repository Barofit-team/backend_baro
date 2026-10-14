package com.barofit.profile;

import com.barofit.identity.api.event.UserWithdrawn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 탈퇴한 사용자의 profile 데이터를 정리한다. identity 트랜잭션이 커밋된 뒤 새 트랜잭션에서 동기로 처리한다.
 * 실패해도 탈퇴 자체는 이미 커밋됐으므로 예외를 삼키고 ERROR 로그로 남겨 수동 복구한다.
 * 서비스를 분리하거나 신뢰성 요구가 커지면 outbox 방식으로 전환한다.
 */
@Slf4j
@Component
class ProfileWithdrawalListener {

	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void on(UserWithdrawn event) {
		try {
			// profile 테이블이 생기면 여기서 event.userId()의 데이터를 삭제한다
		} catch (RuntimeException e) {
			log.error("Failed to clean up withdrawn user data. module=profile, userId={}", event.userId(), e);
		}
	}
}
