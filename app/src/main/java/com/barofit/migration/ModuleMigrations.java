package com.barofit.migration;

import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import com.barofit.common.migration.ModuleSchema;

/** 모듈 schema마다 Flyway를 따로 실행한다 */
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
