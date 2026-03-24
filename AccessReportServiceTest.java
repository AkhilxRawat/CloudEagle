package com.cloudeagle.githubaccess;

import com.cloudeagle.githubaccess.config.GitHubProperties;
import com.cloudeagle.githubaccess.dto.OrgAccessReportResponse;
import com.cloudeagle.githubaccess.model.GitHubPermissions;
import com.cloudeagle.githubaccess.model.GitHubRepo;
import com.cloudeagle.githubaccess.model.GitHubUser;
import com.cloudeagle.githubaccess.service.AccessReportService;
import com.cloudeagle.githubaccess.service.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessReportServiceTest {

    @Mock private GitHubApiClient apiClient;
    private AccessReportService service;

    @BeforeEach
    void setUp() {
        GitHubProperties props = new GitHubProperties();
        props.setToken("test-token");
        props.setParallelism(2);
        service = new AccessReportService(apiClient, props);
    }

    @Test
    void generateReport_aggregatesUsersAcrossRepos() {
        GitHubRepo repo1 = new GitHubRepo();
        repo1.setId(1L); repo1.setName("repo-a"); repo1.setFullName("myorg/repo-a");
        repo1.setHtmlUrl("https://github.com/myorg/repo-a"); repo1.setVisibility("private");

        GitHubRepo repo2 = new GitHubRepo();
        repo2.setId(2L); repo2.setName("repo-b"); repo2.setFullName("myorg/repo-b");
        repo2.setHtmlUrl("https://github.com/myorg/repo-b"); repo2.setVisibility("public");

        GitHubUser alice = mockUser("alice", "User", true, true, false);
        GitHubUser bob   = mockUser("bob",   "User", false, false, true);

        when(apiClient.getOrgRepositories("myorg")).thenReturn(List.of(repo1, repo2));
        when(apiClient.getRepoCollaborators("myorg", "repo-a")).thenReturn(List.of(alice, bob));
        when(apiClient.getRepoCollaborators("myorg", "repo-b")).thenReturn(List.of(alice));

        OrgAccessReportResponse report = service.generateReport("myorg");

        assertThat(report.getOrganization()).isEqualTo("myorg");
        assertThat(report.getTotalRepositories()).isEqualTo(2);
        assertThat(report.getTotalUsers()).isEqualTo(2);

        var aliceReport = report.getUserAccessReports().stream()
            .filter(u -> u.getUsername().equals("alice")).findFirst().orElseThrow();
        assertThat(aliceReport.getTotalRepos()).isEqualTo(2);
        assertThat(aliceReport.getRepositories().get(0).isAdmin()).isTrue();

        var bobReport = report.getUserAccessReports().stream()
            .filter(u -> u.getUsername().equals("bob")).findFirst().orElseThrow();
        assertThat(bobReport.getTotalRepos()).isEqualTo(1);
        assertThat(bobReport.getRepositories().get(0).isRead()).isTrue();
    }

    @Test
    void generateReport_emptyOrg_returnsEmptyReport() {
        when(apiClient.getOrgRepositories("empty-org")).thenReturn(List.of());
        OrgAccessReportResponse report = service.generateReport("empty-org");
        assertThat(report.getTotalRepositories()).isZero();
        assertThat(report.getTotalUsers()).isZero();
    }

    private GitHubUser mockUser(String login, String type, boolean admin, boolean push, boolean pull) {
        GitHubUser user = new GitHubUser();
        user.setLogin(login);
        user.setType(type);
        user.setHtmlUrl("https://github.com/" + login);
        GitHubPermissions perms = new GitHubPermissions();
        perms.setAdmin(admin); perms.setPush(push); perms.setPull(pull);
        user.setPermissions(perms);
        return user;
    }
}
