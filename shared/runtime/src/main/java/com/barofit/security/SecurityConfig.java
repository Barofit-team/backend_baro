package com.barofit.security;




import org.springframework.http.HttpMethod;
import java.util.ArrayList;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** 바로핏 JWT 검증 설정 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	private static final String[] AUTH_PUBLIC_PATHS = {"/api/v1/auth/**", "/.well-known/jwks.json"};
    private static final String[] ROUTINE_PUBLIC_PATHS = {"/api/v1/exercises", "/api/v1/exercises/*", "/api/v1/routine-templates"};

	private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

	private static final String[] INFRA_PUBLIC_PATHS = {"/actuator/health", "/actuator/health/**", "/error"};
    private static final String[] DOCS_PUBLIC_PATHS = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**"};

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder barofitJwtDecoder,
			AuthenticationEntryPoint authenticationEntryPoint) throws Exception {
		http
				.cors(cors -> {})
				.csrf(csrf -> csrf.disable())
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(AUTH_PUBLIC_PATHS).permitAll()
						.requestMatchers(INFRA_PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, ROUTINE_PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, DOCS_PUBLIC_PATHS).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt
								.decoder(barofitJwtDecoder)
								.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.bearerTokenResolver(bearerTokenResolver())
						.authenticationEntryPoint(authenticationEntryPoint))
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint));
		return http.build();
	}

	@Bean
	org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource(
			@Value("${barofit.cors.allowed-origins:}") String origins) {
		var configuration = new org.springframework.web.cors.CorsConfiguration();
		configuration.setAllowedOrigins(Arrays.stream(origins.split(","))
				.map(String::trim).filter(origin -> !origin.isEmpty()).toList());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuration.setExposedHeaders(List.of("Location"));
		var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

	@Bean
	JwtDecoder barofitJwtDecoder(@Value("${barofit.jwt.public-jwks-path}") String publicJwksPath, Clock clock)
			throws IOException, ParseException {
		// 키 교체를 위해 JWK Set 사용
		JWKSet jwks = JWKSet.load(Path.of(publicJwksPath).toFile()).toPublicJWKSet();
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(jwks)).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				new AccessTokenTimestampValidator(CLOCK_SKEW, clock),
				new JwtIssuerValidator("barofit-identity"),
				new JwtAudienceValidator("barofit-api")));
		return decoder;
	}

	@Bean
	AuthenticationEntryPoint authenticationEntryPoint(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
		return new BarofitAuthenticationEntryPoint(handlerExceptionResolver);
	}

	/** 공개 경로에서는 Bearer 헤더를 무시한다(만료 토큰으로 refresh 호출 허용) */
	static BearerTokenResolver bearerTokenResolver() {
		DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
		List<RequestMatcher> matchers = new ArrayList<>(Arrays.stream(AUTH_PUBLIC_PATHS)
				.<RequestMatcher>map(path -> PathPatternRequestMatcher.withDefaults().matcher(path))
				.toList());
        for (String path : ROUTINE_PUBLIC_PATHS) {
            matchers.add(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, path));
        }
        for (String path : DOCS_PUBLIC_PATHS) {
            matchers.add(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, path));
        }
		RequestMatcher publicPaths = new OrRequestMatcher(matchers);
		return request -> publicPaths.matches(request) ? null : delegate.resolve(request);
	}

	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("role");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}
}
