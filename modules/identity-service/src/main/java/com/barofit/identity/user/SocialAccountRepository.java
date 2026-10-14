package com.barofit.identity.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

	Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	@Modifying(flushAutomatically = true)
	@Query("delete from SocialAccount a where a.userId = :userId")
	int deleteAllByUserId(UUID userId);
}
