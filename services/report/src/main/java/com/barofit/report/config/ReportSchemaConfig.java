package com.barofit.report.config;

import com.barofit.common.migration.ModuleSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ReportSchemaConfig {
    @Bean ModuleSchema reportSchema() { return new ModuleSchema("report"); }
}
