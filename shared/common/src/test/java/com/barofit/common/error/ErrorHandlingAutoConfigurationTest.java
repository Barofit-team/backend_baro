package com.barofit.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class ErrorHandlingAutoConfigurationTest {

	private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ErrorHandlingAutoConfiguration.class));

	@Test
	void 컴포넌트_스캔_없이도_예외_핸들러를_등록한다() {
		runner.run(context -> assertThat(context).hasSingleBean(GlobalExceptionHandler.class));
	}

	@Test
	void 이미_등록된_핸들러가_있으면_중복_등록하지_않는다() {
		runner.withBean(GlobalExceptionHandler.class)
				.run(context -> assertThat(context).hasSingleBean(GlobalExceptionHandler.class));
	}
}
