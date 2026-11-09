package com.barofit.report.config;

import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportJacksonConfig {

    @Bean
    public ObjectMapper reportObjectMapper() {
        return new ObjectMapper();
    }
}