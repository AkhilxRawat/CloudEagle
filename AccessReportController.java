package com.cloudeagle.githubaccess.controller;

import com.cloudeagle.githubaccess.dto.OrgAccessReportResponse;
import com.cloudeagle.githubaccess.service.AccessReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
@Tag(name = "Access Report", description = "GitHub organization repository access reporting")
public class AccessReportController {

    private final AccessReportService reportService;

    @GetMapping("/orgs/{org}/access-report")
    @Operation(
        summary = "Get organization access report",
        description = "Generates a full report mapping every user to the repositories they can access within the given GitHub organization."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Report generated successfully",
            content = @Content(schema = @Schema(implementation = OrgAccessReportResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid or missing GitHub token"),
        @ApiResponse(responseCode = "403", description = "Token lacks required permissions"),
        @ApiResponse(responseCode = "404", description = "Organization not found"),
        @ApiResponse(responseCode = "429", description = "GitHub API rate limit exceeded")
    })
    public ResponseEntity<OrgAccessReportResponse> getAccessReport(
        @Parameter(description = "GitHub organization name", example = "my-org", required = true)
        @PathVariable String org
    ) {
        if (org == null || org.isBlank()) {
            throw new IllegalArgumentException("Organization name must not be blank");
        }
        OrgAccessReportResponse report = reportService.generateReport(org);
        return ResponseEntity.ok(report);
    }
}
