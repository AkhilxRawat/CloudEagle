package com.cloudeagle.githubaccess.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

@Configuration
public class GitHubHttpConfig {
    @Autowired private GitHubProperties props;

    @Bean
    public RestTemplate gitHubRestTemplate(RestTemplateBuilder builder) {
        return builder
            .defaultHeader("Authorization", "Bearer " + props.getToken())
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .build();
    }
}
