package com.barofit.report.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportJacksonConfig {

    @Bean
    public ObjectMapper reportObjectMapper() {
        return new ObjectMapper();
    }
}