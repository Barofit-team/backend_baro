package com.barofit.workout;

import com.barofit.common.migration.ModuleSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class WorkoutConfig {
    @Bean ModuleSchema workoutSchema() { return new ModuleSchema("workout"); }

    @Bean OpenAPI workoutOpenApi() {
        return new OpenAPI().info(new Info().title("Barofit Workout API").version("v1")
                .description("온디바이스 운동 세션 동기화와 코칭 음성"))
            .components(new Components().addSecuritySchemes("workoutAuth",
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
