package com.barofit.migration;

import java.util.List;
import javax.sql.DataSource;
import com.barofit.common.migration.ModuleSchema;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ModuleMigrationConfig {

	@Bean
	ModuleMigrations moduleMigrations(DataSource dataSource, List<ModuleSchema> schemas) {
		return new ModuleMigrations(dataSource, schemas);
	}

	// Hibernate validate보다 마이그레이션이 먼저 실행되도록 순서를 보장한다
	@Bean
	static EntityManagerFactoryDependsOnPostProcessor entityManagerFactoryDependsOnModuleMigrations() {
		return new EntityManagerFactoryDependsOnPostProcessor(ModuleMigrations.class);
	}
}
