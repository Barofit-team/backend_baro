package com.barofit.identity.image;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProfileImageProperties.class)
class ProfileImageConfig {

	@Bean
	@ConditionalOnProperty(name = "barofit.profile-image.storage", havingValue = "temporary", matchIfMissing = true)
	ProfileImageStorage temporaryProfileImageStorage(ProfileImageProperties properties) {
		return new TemporaryProfileImageStorage(properties);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "barofit.profile-image.storage", havingValue = "s3")
	static class S3StorageConfig {

		// 가입 응답이 사진 업로드를 기다리므로 짧게 둔다
		private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
		private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(5);
		private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(10);

		/** 자격 증명은 기본 체인(인스턴스 프로파일·Pod Identity·IRSA·환경 변수). 키를 설정 파일에 두지 않는다 */
		@Bean
		S3Client profileImageS3Client(ProfileImageProperties properties) {
			S3ProfileImageStorage.validate(properties);
			return S3Client.builder()
					.region(Region.of(properties.s3().region()))
					.credentialsProvider(DefaultCredentialsProvider.builder().build())
					.httpClientBuilder(UrlConnectionHttpClient.builder()
							.connectionTimeout(CONNECT_TIMEOUT)
							.socketTimeout(SOCKET_TIMEOUT))
					.overrideConfiguration(config -> config.apiCallTimeout(API_CALL_TIMEOUT))
					.build();
		}

		@Bean
		ProfileImageStorage s3ProfileImageStorage(ProfileImageProperties properties, S3Client profileImageS3Client) {
			return new S3ProfileImageStorage(properties, profileImageS3Client);
		}
	}
}
