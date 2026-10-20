package com.barofit.identity.profile;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

	// 운동 목표는 FK ON DELETE CASCADE로 함께 지워진다
	@Modifying(flushAutomatically = true)
	@Query("delete from UserProfile p where p.userId = :userId")
	int deleteByUserId(UUID userId);
}
