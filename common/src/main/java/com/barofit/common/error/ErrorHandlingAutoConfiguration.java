package com.barofit.common.error;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * common을 jar로 떼어내도 그대로 동작하도록 컴포넌트 스캔이 아닌 자동설정으로 등록한다.
 * 모놀리스에서는 app의 컴포넌트 스캔에도 잡히므로 중복 등록을 막는다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ErrorHandlingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	GlobalExceptionHandler globalExceptionHandler() {
		return new GlobalExceptionHandler();
	}
}
