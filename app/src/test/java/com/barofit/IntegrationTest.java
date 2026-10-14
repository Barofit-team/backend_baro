package com.barofit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** DB 통합 테스트. 컨텍스트와 PostgreSQL 컨테이너를 공유한다 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestJwtKeyConfiguration.class})
public @interface IntegrationTest {
}
