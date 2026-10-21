package com.barofit.identity.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

	/** 같은 사용자의 온보딩 동시 요청을 직렬화한다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> findByIdForUpdate(UUID id);

	/** uk_users_nickname_onboarded와 같은 조건 */
	@Query("""
			select count(u) > 0 from User u
			where lower(u.nickname) = lower(:nickname)
			  and u.onboarded = true
			  and u.status = com.barofit.identity.user.UserStatus.ACTIVE""")
	boolean existsOnboardedNickname(String nickname);
}
