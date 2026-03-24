package com.cloudeagle.githubaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Details of a single repository a user has access to")
public class RepoAccessEntry {
    @Schema(example = "backend-service") private String repoName;
    @Schema(example = "my-org/backend-service") private String repoFullName;
    @Schema(example = "https://github.com/my-org/backend-service") private String repoUrl;
    private boolean privateRepo;
    @Schema(description = "public, private, or internal") private String visibility;
    @Schema(example = "write") private String role;
    private boolean admin;
    private boolean write;
    private boolean read;
}
