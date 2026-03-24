package com.cloudeagle.githubaccess.service;

import com.cloudeagle.githubaccess.config.GitHubProperties;
import com.cloudeagle.githubaccess.exception.GitHubApiException;
import com.cloudeagle.githubaccess.model.GitHubRepo;
import com.cloudeagle.githubaccess.model.GitHubUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Low-level client for GitHub REST API.
 * Handles authentication, pagination, and error mapping.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubApiClient {

    private final RestTemplate gitHubRestTemplate;
    private final GitHubProperties props;

    // Regex to extract 'next' page URL from Link header
    private static final Pattern NEXT_LINK_PATTERN =
        Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    /**
     * Retrieves all repositories for the given organization (handles pagination automatically).
     */
    @Cacheable(value = "repos", key = "#org")
    public List<GitHubRepo> getOrgRepositories(String org) {
        String url = props.getApiBaseUrl() + "/orgs/" + org + "/repos?per_page=" + props.getPageSize() + "&type=all";
        log.info("Fetching repositories for org: {}", org);
        return fetchAllPages(url, new ParameterizedTypeReference<List<GitHubRepo>>() {});
    }

    /**
     * Retrieves all collaborators for a given repo (handles pagination automatically).
     * Uses affiliation=all to include direct, team, and org-level access.
     */
    @Cacheable(value = "collaborators", key = "#org + '/' + #repoName")
    public List<GitHubUser> getRepoCollaborators(String org, String repoName) {
        String url = props.getApiBaseUrl() + "/repos/" + org + "/" + repoName
            + "/collaborators?per_page=" + props.getPageSize() + "&affiliation=all";
        log.debug("Fetching collaborators for repo: {}/{}", org, repoName);
        try {
            return fetchAllPages(url, new ParameterizedTypeReference<List<GitHubUser>>() {});
        } catch (GitHubApiException e) {
            // 403 on a repo means the token lacks access — return empty rather than aborting the whole run
            if (e.getStatus() == HttpStatus.FORBIDDEN) {
                log.warn("Insufficient permissions to list collaborators for {}/{}, skipping.", org, repoName);
                return Collections.emptyList();
            }
            throw e;
        }
    }

    /**
     * Generic paginated fetcher.
     * GitHub uses Link headers for cursor-based pagination; we follow 'next' until exhausted.
     */
    private <T> List<T> fetchAllPages(String firstUrl, ParameterizedTypeReference<List<T>> type) {
        List<T> result = new ArrayList<>();
        String nextUrl = firstUrl;

        while (nextUrl != null) {
            ResponseEntity<List<T>> response = doGet(nextUrl, type);
            if (response.getBody() != null) {
                result.addAll(response.getBody());
            }
            nextUrl = extractNextPageUrl(response);
        }

        return result;
    }

    private <T> ResponseEntity<List<T>> doGet(String url, ParameterizedTypeReference<List<T>> type) {
        try {
            return gitHubRestTemplate.exchange(url, HttpMethod.GET, null, type);
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
            String message = switch (status) {
                case UNAUTHORIZED -> "GitHub token is invalid or expired.";
                case FORBIDDEN    -> "GitHub token lacks required permissions.";
                case NOT_FOUND    -> "Organization or resource not found: " + url;
                case TOO_MANY_REQUESTS -> "GitHub API rate limit exceeded. Please retry later.";
                default -> "GitHub API error: " + e.getMessage();
            };
            throw new GitHubApiException(message, status, e);
        }
    }

    /**
     * Parses GitHub's Link response header to find the URL of the next page.
     * Returns null when there are no more pages.
     */
    private String extractNextPageUrl(ResponseEntity<?> response) {
        List<String> linkHeaders = response.getHeaders().get("Link");
        if (linkHeaders == null || linkHeaders.isEmpty()) return null;

        for (String header : linkHeaders) {
            Matcher matcher = NEXT_LINK_PATTERN.matcher(header);
            if (matcher.find()) return matcher.group(1);
        }
        return null;
    }
}
