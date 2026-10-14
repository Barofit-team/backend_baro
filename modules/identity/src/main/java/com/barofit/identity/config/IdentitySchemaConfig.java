package com.barofit.identity.config;

import com.barofit.common.migration.ModuleSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class IdentitySchemaConfig {

	@Bean
	ModuleSchema identitySchema() {
		return new ModuleSchema("identity");
	}
}
