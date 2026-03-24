package com.cloudeagle.githubaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@Schema(description = "Access report for a single user")
public class UserAccessReport {
    @Schema(example = "john-doe") private String username;
    @Schema(example = "https://github.com/john-doe") private String profileUrl;
    @Schema(description = "User or Bot") private String accountType;
    private int totalRepos;
    private List<RepoAccessEntry> repositories;
}
