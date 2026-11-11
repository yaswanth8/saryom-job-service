package com.saryom.jobservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the interactive docs (Swagger UI at {@code /swagger-ui.html},
 * machine-readable spec at {@code /v3/api-docs}). Declares the Firebase bearer-token
 * scheme so the "Authorize" button lets you exercise authenticated endpoints.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    OpenAPI jobServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Saryom Job Service API")
                        .description("Private job-application tracking and a shared, public recruiter directory.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Firebase ID token issued to the signed-in user.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
