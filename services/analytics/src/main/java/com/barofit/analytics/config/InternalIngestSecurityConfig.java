package com.barofit.analytics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class InternalIngestSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain internalIngestFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/internal/workout-completions/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }
}
