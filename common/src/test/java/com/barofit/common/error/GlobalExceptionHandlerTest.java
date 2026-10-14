package com.barofit.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void 비즈니스_예외는_에러코드의_상태와_code로_응답한다() throws Exception {
		mockMvc.perform(get("/business"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.code").value("TEST_CONFLICT"))
				.andExpect(jsonPath("$.detail").value("충돌"));
	}

	@Test
	void 검증_실패는_INVALID_INPUT과_필드_오류를_응답한다() throws Exception {
		mockMvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.errors[0].field").value("name"));
	}

	@Test
	void Spring_MVC_표준_예외에도_code를_붙인다() throws Exception {
		mockMvc.perform(post("/business"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	void 예상하지_못한_예외는_500_INTERNAL_SERVER_ERROR로_응답한다() throws Exception {
		mockMvc.perform(get("/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
	}

	@RestController
	static class TestController {

		@GetMapping("/business")
		void business() {
			throw new BusinessException(TestErrorCode.TEST_CONFLICT, "충돌");
		}

		@PostMapping("/validate")
		void validate(@Valid @RequestBody TestRequest request) {
		}

		@GetMapping("/unexpected")
		void unexpected() {
			throw new IllegalStateException("boom");
		}
	}

	@Getter
	static class TestRequest {
		@NotBlank
		private String name;
	}

	enum TestErrorCode implements ErrorCode {
		TEST_CONFLICT;

		@Override
		public String code() {
			return name();
		}

		@Override
		public HttpStatus status() {
			return HttpStatus.CONFLICT;
		}

		@Override
		public String message() {
			return "충돌";
		}
	}
}
