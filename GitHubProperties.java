package com.cloudeagle.githubaccess.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {
    @NotBlank(message = "GitHub token must be configured via GITHUB_TOKEN env var or github.token property")
    private String token;
    private String apiBaseUrl = "https://api.github.com";
    private int parallelism = 10;
    private int pageSize = 100;
}
