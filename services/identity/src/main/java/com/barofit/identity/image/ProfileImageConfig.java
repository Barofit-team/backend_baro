package com.barofit.identity.image;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProfileImageProperties.class)
class ProfileImageConfig {

	@Bean
	@ConditionalOnProperty(name = "barofit.profile-image.storage", havingValue = "temporary", matchIfMissing = true)
	ProfileImageStorage temporaryProfileImageStorage(ProfileImageProperties properties) {
		return new TemporaryProfileImageStorage(properties);
	}

	@Bean
	@ConditionalOnProperty(name = "barofit.profile-image.storage", havingValue = "s3")
	ProfileImageStorage s3ProfileImageStorage(ProfileImageProperties properties) {
		return new S3ProfileImageStorage(properties);
	}
}
