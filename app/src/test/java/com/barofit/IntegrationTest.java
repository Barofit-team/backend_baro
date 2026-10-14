package com.barofit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * DB가 필요한 통합 테스트. 모든 테스트가 같은 컨텍스트와 PostgreSQL 컨테이너를 공유한다.
 * 모듈별 마이그레이션 실행기가 app에 있으므로, DB가 필요한 도메인 모듈 테스트도 app에 둔다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestJwtKeyConfiguration.class})
public @interface IntegrationTest {
}
