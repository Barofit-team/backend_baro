package com.barofit;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** 공유 PostgreSQL 컨테이너. 이미지 태그는 compose.yaml에서 읽는다 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final String IMAGE_PROPERTY = "barofit.test.postgres-image";

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(postgresImage()));

	static {
		POSTGRES.start();
	}

	@Bean(destroyMethod = "")
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return POSTGRES;
	}

	private static String postgresImage() {
		String image = System.getProperty(IMAGE_PROPERTY);
		if (image == null || image.isBlank()) {
			throw new IllegalStateException(IMAGE_PROPERTY + " 시스템 프로퍼티가 없습니다. 테스트는 Gradle로 실행하세요");
		}
		return image;
	}
}
