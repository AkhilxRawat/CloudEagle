package com.cloudeagle.githubaccess;

import com.cloudeagle.githubaccess.dto.OrgAccessReportResponse;
import com.cloudeagle.githubaccess.dto.UserAccessReport;
import com.cloudeagle.githubaccess.exception.GitHubApiException;
import com.cloudeagle.githubaccess.service.AccessReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = com.cloudeagle.githubaccess.controller.AccessReportController.class)
class AccessReportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  AccessReportService reportService;

    @Test
    void getAccessReport_returns200WithReport() throws Exception {
        OrgAccessReportResponse mockReport = OrgAccessReportResponse.builder()
            .organization("my-org")
            .generatedAt(Instant.parse("2024-01-01T00:00:00Z"))
            .totalRepositories(1)
            .totalUsers(1)
            .userAccessReports(List.of(
                UserAccessReport.builder()
                    .username("alice")
                    .profileUrl("https://github.com/alice")
                    .accountType("User")
                    .totalRepos(1)
                    .repositories(List.of())
                    .build()
            ))
            .build();

        when(reportService.generateReport("my-org")).thenReturn(mockReport);

        mockMvc.perform(get("/api/v1/github/orgs/my-org/access-report")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organization").value("my-org"))
            .andExpect(jsonPath("$.totalRepositories").value(1))
            .andExpect(jsonPath("$.totalUsers").value(1))
            .andExpect(jsonPath("$.userAccessReports[0].username").value("alice"));
    }

    @Test
    void getAccessReport_whenOrgNotFound_returns404() throws Exception {
        when(reportService.generateReport("ghost-org"))
            .thenThrow(new GitHubApiException("Organization or resource not found", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/github/orgs/ghost-org/access-report"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Organization or resource not found"));
    }

    @Test
    void getAccessReport_whenRateLimited_returns429() throws Exception {
        when(reportService.generateReport("busy-org"))
            .thenThrow(new GitHubApiException("GitHub API rate limit exceeded. Please retry later.", HttpStatus.TOO_MANY_REQUESTS));

        mockMvc.perform(get("/api/v1/github/orgs/busy-org/access-report"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error").value("GitHub API rate limit exceeded. Please retry later."));
    }

    @Test
    void getAccessReport_whenUnauthorized_returns401() throws Exception {
        when(reportService.generateReport("locked-org"))
            .thenThrow(new GitHubApiException("GitHub token is invalid or expired.", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/github/orgs/locked-org/access-report"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("GitHub token is invalid or expired."));
    }
}
