package io.quizchef.common.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API documentation metadata for the QuizChef OpenAPI specification.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI quizChefOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuizChef API")
                        .description("REST API for the QuizChef live quiz platform.")
                        .version("0.1.0")
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from POST /api/v1/auth/login")));
    }
}
