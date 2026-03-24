package com.cloudeagle.githubaccess.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@Schema(description = "Full access report for a GitHub organization")
public class OrgAccessReportResponse {
    @Schema(example = "my-org") private String organization;
    private Instant generatedAt;
    private int totalRepositories;
    private int totalUsers;
    private List<UserAccessReport> userAccessReports;
}
