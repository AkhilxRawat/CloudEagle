package com.cloudeagle.githubaccess.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("GitHub Access Report API")
                .description("Generates access reports showing which users can access which repositories in a GitHub organization")
                .version("1.0.0")
                .contact(new Contact().name("CloudEagle").url("https://cloudeagle.ai")));
    }
}
