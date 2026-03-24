package com.cloudeagle.githubaccess.service;

import com.cloudeagle.githubaccess.config.GitHubProperties;
import com.cloudeagle.githubaccess.dto.OrgAccessReportResponse;
import com.cloudeagle.githubaccess.dto.RepoAccessEntry;
import com.cloudeagle.githubaccess.dto.UserAccessReport;
import com.cloudeagle.githubaccess.model.GitHubPermissions;
import com.cloudeagle.githubaccess.model.GitHubRepo;
import com.cloudeagle.githubaccess.model.GitHubUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Core service for generating the organization access report.
 *
 * Scale design decisions:
 * - Repositories are fetched in one paginated call (all at once, not per-repo serially).
 * - Collaborator lookups are parallelized using a fixed thread pool bounded by github.parallelism.
 * - Results are aggregated in a ConcurrentHashMap keyed by username.
 * - Caching (Caffeine, 10 min TTL) prevents duplicate API calls within a session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessReportService {

    private final GitHubApiClient apiClient;
    private final GitHubProperties props;

    public OrgAccessReportResponse generateReport(String org) {
        log.info("Generating access report for org: {}", org);

        List<GitHubRepo> repos = apiClient.getOrgRepositories(org);
        log.info("Found {} repositories in org '{}'", repos.size(), org);

        // Map: username -> list of RepoAccessEntry (built concurrently)
        Map<String, GitHubUser> userIndex = new ConcurrentHashMap<>();
        Map<String, List<RepoAccessEntry>> userRepoMap = new ConcurrentHashMap<>();

        // Use a bounded thread pool to parallelize collaborator lookups
        ExecutorService executor = Executors.newFixedThreadPool(props.getParallelism());
        List<Future<?>> futures = new ArrayList<>();

        for (GitHubRepo repo : repos) {
            futures.add(executor.submit(() -> {
                List<GitHubUser> collaborators = apiClient.getRepoCollaborators(org, repo.getName());

                for (GitHubUser user : collaborators) {
                    // Track user metadata (idempotent — same user across repos)
                    userIndex.putIfAbsent(user.getLogin(), user);

                    RepoAccessEntry entry = buildRepoEntry(repo, user);

                    userRepoMap
                        .computeIfAbsent(user.getLogin(), k -> new ArrayList<>())
                        .add(entry);
                }
            }));
        }

        // Wait for all collaborator fetches to complete
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                log.error("Error fetching collaborators for a repository", e);
            }
        }
        executor.shutdown();

        // Build sorted per-user reports
        List<UserAccessReport> userReports = userRepoMap.entrySet().stream()
            .map(entry -> {
                String username = entry.getKey();
                List<RepoAccessEntry> repoEntries = entry.getValue();
                repoEntries.sort(Comparator.comparing(RepoAccessEntry::getRepoName));

                GitHubUser user = userIndex.get(username);
                return UserAccessReport.builder()
                    .username(username)
                    .profileUrl(user != null ? user.getHtmlUrl() : null)
                    .accountType(user != null ? user.getType() : "User")
                    .totalRepos(repoEntries.size())
                    .repositories(repoEntries)
                    .build();
            })
            .sorted(Comparator.comparing(UserAccessReport::getUsername))
            .toList();

        log.info("Report complete: {} repos, {} users", repos.size(), userReports.size());

        return OrgAccessReportResponse.builder()
            .organization(org)
            .generatedAt(Instant.now())
            .totalRepositories(repos.size())
            .totalUsers(userReports.size())
            .userAccessReports(userReports)
            .build();
    }

    private RepoAccessEntry buildRepoEntry(GitHubRepo repo, GitHubUser user) {
        GitHubPermissions perms = user.getPermissions();
        boolean isAdmin = perms != null && perms.isAdmin();
        boolean canWrite = perms != null && perms.isPush();
        boolean canRead  = perms != null && perms.isPull();

        // Determine the highest role label
        String role = user.getRoleName() != null ? user.getRoleName()
            : (isAdmin ? "admin" : canWrite ? "write" : "read");

        return RepoAccessEntry.builder()
            .repoName(repo.getName())
            .repoFullName(repo.getFullName())
            .repoUrl(repo.getHtmlUrl())
            .privateRepo(repo.isPrivateRepo())
            .visibility(repo.getVisibility())
            .role(role)
            .admin(isAdmin)
            .write(canWrite)
            .read(canRead)
            .build();
    }
}
