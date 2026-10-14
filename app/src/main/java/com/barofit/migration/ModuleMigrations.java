package com.barofit.migration;

import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import com.barofit.common.migration.ModuleSchema;

/**
 * 모듈마다 독립된 Flyway 인스턴스로 마이그레이션한다. 이력 테이블은 각 모듈의 schema 안에 생긴다.
 * Spring Boot의 Flyway 자동설정을 쓰지 않으므로, EntityManagerFactory가 이 Bean에 의존하도록 등록한다(ModuleMigrationConfig).
 */
@Slf4j
public class ModuleMigrations {

	ModuleMigrations(DataSource dataSource, List<ModuleSchema> schemas) {
		for (ModuleSchema schema : schemas) {
			var result = Flyway.configure()
					.dataSource(dataSource)
					.schemas(schema.name())
					.locations(schema.migrationLocation())
					.createSchemas(true)
					.load()
					.migrate();
			log.info("Flyway migrated schema '{}' ({} migrations applied)", schema.name(), result.migrationsExecuted);
		}
	}
}
