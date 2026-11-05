package com.barofit.analytics.config;

import com.barofit.common.migration.ModuleSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AnalyticsSchemaConfig {
    @Bean ModuleSchema analyticsSchema() { return new ModuleSchema("analytics"); }
}
