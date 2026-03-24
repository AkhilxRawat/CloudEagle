# GitHub Access Report Service

> A Spring Boot REST API that connects to GitHub and generates a structured report showing **which users have access to which repositories** within a GitHub organization.

---

## Table of Contents

- [What This Project Does](#what-this-project-does)
- [How It Works](#how-it-works)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Authentication Setup](#authentication-setup)
- [Running the Application](#running-the-application)
- [Running with Docker](#running-with-docker)
- [API Reference](#api-reference)
- [Running Tests](#running-tests)
- [Configuration](#configuration)
- [Design Decisions](#design-decisions)
- [Error Handling](#error-handling)

---

## What This Project Does

In large organizations, it's hard to know who has access to what on GitHub. This service solves that by:

1. Connecting to the GitHub API using a Personal Access Token
2. Fetching all repositories in a given organization
3. Finding every user who has access to each repository (including their permission level)
4. Returning a clean, aggregated JSON report — one entry per user, listing all their accessible repos

**Example use case:** An IT/security team at a company wants to audit which engineers can push to production repositories. They call this API, get the full access map, and pipe it into their compliance tooling.

---

## How It Works

```
Client Request
     │
     ▼
AccessReportController        ← Receives GET /api/v1/github/orgs/{org}/access-report
     │
     ▼
AccessReportService           ← Orchestrates the report generation
     │
     ├── GitHubApiClient ──── Fetch all org repositories (paginated)
     │
     ├── [Parallel threads] ─ For each repo, fetch all collaborators (paginated)
     │        Thread 1: repo-a → [alice: admin, bob: read]
     │        Thread 2: repo-b → [alice: write, carol: read]
     │        Thread N: ...
     │
     └── Aggregate results   ← Build user → [repos] map, return as JSON
```

**Key points:**
- All GitHub API calls are **fully paginated** — no data is missed even for orgs with 1000+ users
- Collaborator lookups run in **parallel** (10 threads by default) to handle 100+ repos efficiently
- Results are **cached for 10 minutes** to avoid redundant API calls on repeat requests

---

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 17 | Core language |
| Spring Boot 3.2 | Application framework |
| Spring Web (RestTemplate) | HTTP client for GitHub API calls |
| Spring Cache + Caffeine | In-memory caching (10 min TTL) |
| Lombok | Reduces boilerplate (getters, builders, etc.) |
| SpringDoc OpenAPI 2 | Auto-generates Swagger UI |
| JUnit 5 + Mockito | Unit testing |
| MockMvc | Controller integration testing |
| Docker | Containerization |

---

## Project Structure

```
github-access-report/
│
├── src/main/java/com/cloudeagle/githubaccess/
│   │
│   ├── GitHubAccessReportApplication.java     ← Spring Boot entry point
│   │
│   ├── config/
│   │   ├── GitHubProperties.java              ← Binds github.* properties (token, parallelism, etc.)
│   │   ├── GitHubHttpConfig.java              ← Creates RestTemplate with auth headers pre-set
│   │   ├── CacheConfig.java                   ← Caffeine cache setup (10 min TTL, 500 max entries)
│   │   └── OpenApiConfig.java                 ← Swagger/OpenAPI title and metadata
│   │
│   ├── model/                                 ← Shapes of GitHub API responses (deserialized JSON)
│   │   ├── GitHubRepo.java                    ← Repository: name, visibility, url, etc.
│   │   ├── GitHubUser.java                    ← User/collaborator: login, type, permissions
│   │   └── GitHubPermissions.java             ← admin, maintain, push, triage, pull flags
│   │
│   ├── dto/                                   ← Shapes of our own API responses
│   │   ├── OrgAccessReportResponse.java       ← Top-level report: org name, timestamp, user list
│   │   ├── UserAccessReport.java              ← Per-user: username, account type, repo list
│   │   └── RepoAccessEntry.java               ← Per-repo: name, url, role, permission flags
│   │
│   ├── service/
│   │   ├── GitHubApiClient.java               ← All GitHub API calls; handles pagination + caching
│   │   └── AccessReportService.java           ← Parallel aggregation; builds the final report
│   │
│   ├── controller/
│   │   └── AccessReportController.java        ← Exposes the REST endpoint with Swagger annotations
│   │
│   └── exception/
│       ├── GitHubApiException.java            ← Custom exception with HTTP status
│       └── GlobalExceptionHandler.java        ← Maps exceptions to clean JSON error responses
│
├── src/test/java/com/cloudeagle/githubaccess/
│   ├── AccessReportServiceTest.java           ← Unit tests: aggregation logic with mocked API client
│   └── AccessReportControllerTest.java        ← Integration tests: endpoint behavior with MockMvc
│
├── src/main/resources/
│   └── application.yml                        ← All app configuration
│
├── pom.xml                                    ← Maven dependencies
├── Dockerfile                                 ← Multi-stage Docker build
├── docker-compose.yml                         ← One-command Docker run
└── .gitignore
```

---

## Prerequisites

- **Java 17** or higher
- **Maven 3.8** or higher
- A **GitHub Personal Access Token (PAT)** — see Authentication Setup below

---

## Authentication Setup

This service authenticates with GitHub using a **Personal Access Token (PAT)**. The token is sent as a `Bearer` header on every API request.

### Step 1 — Create a GitHub PAT

1. Go to **GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)**
2. Click **"Generate new token"**
3. Select the following scopes:
   - `repo` — read access to private repositories
   - `read:org` — list org repositories and members
   - `read:user` — read user profile information
4. Copy the generated token (starts with `ghp_`)

### Step 2 — Configure the token

**Option A — Environment variable (recommended for all environments):**
```bash
export GITHUB_TOKEN=ghp_your_token_here
```

**Option B — Local config file (for local dev only, never commit this):**

Create the file `src/main/resources/application-local.yml`:
```yaml
github:
  token: ghp_your_token_here
```
Then run with the `local` profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

> ⚠️ **Never commit your token.** The `application-local.yml` file is listed in `.gitignore`.

---

## Running the Application

### Option 1 — Maven (recommended for development)

```bash
# Clone the project
git clone https://github.com/<your-username>/github-access-report.git
cd github-access-report

# Set your token
export GITHUB_TOKEN=ghp_your_token_here

# Run directly
mvn spring-boot:run
```

### Option 2 — Build JAR and run

```bash
mvn clean package -DskipTests
java -jar target/github-access-report-1.0.0.jar
```

The server starts at **http://localhost:8080**

---

## Running with Docker

```bash
# Build and start with docker-compose (reads GITHUB_TOKEN from your environment)
export GITHUB_TOKEN=ghp_your_token_here
docker-compose up --build
```

Or with plain Docker:
```bash
docker build -t github-access-report .
docker run -p 8080:8080 -e GITHUB_TOKEN=ghp_your_token_here github-access-report
```

---

## API Reference

### `GET /api/v1/github/orgs/{org}/access-report`

Generates a full access report for the specified GitHub organization.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `org` | path | ✅ | GitHub organization name (e.g., `microsoft`) |

**Example request:**
```bash
curl http://localhost:8080/api/v1/github/orgs/my-org/access-report
```

**Example response:**
```json
{
  "organization": "my-org",
  "generatedAt": "2024-03-15T10:30:00Z",
  "totalRepositories": 3,
  "totalUsers": 2,
  "userAccessReports": [
    {
      "username": "alice",
      "profileUrl": "https://github.com/alice",
      "accountType": "User",
      "totalRepos": 2,
      "repositories": [
        {
          "repoName": "backend-service",
          "repoFullName": "my-org/backend-service",
          "repoUrl": "https://github.com/my-org/backend-service",
          "privateRepo": true,
          "visibility": "private",
          "role": "admin",
          "admin": true,
          "write": true,
          "read": true
        },
        {
          "repoName": "frontend-app",
          "repoFullName": "my-org/frontend-app",
          "repoUrl": "https://github.com/my-org/frontend-app",
          "privateRepo": false,
          "visibility": "public",
          "role": "write",
          "admin": false,
          "write": true,
          "read": true
        }
      ]
    },
    {
      "username": "bob",
      "profileUrl": "https://github.com/bob",
      "accountType": "User",
      "totalRepos": 1,
      "repositories": [
        {
          "repoName": "frontend-app",
          "repoFullName": "my-org/frontend-app",
          "repoUrl": "https://github.com/my-org/frontend-app",
          "privateRepo": false,
          "visibility": "public",
          "role": "read",
          "admin": false,
          "write": false,
          "read": true
        }
      ]
    }
  ]
}
```

### Interactive Docs (Swagger UI)

Once the app is running, open your browser at:
```
http://localhost:8080/swagger-ui.html
```

This gives you a full interactive UI to explore and test the API.

---

## Running Tests

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=AccessReportServiceTest
mvn test -Dtest=AccessReportControllerTest
```

**Test coverage includes:**
- `AccessReportServiceTest` — unit tests for the aggregation logic: verifies users are correctly mapped to repos, handles empty orgs, checks permission flags
- `AccessReportControllerTest` — MockMvc integration tests: verifies 200 OK, 401, 404, and 429 responses are returned correctly

---

## Configuration

All settings live in `src/main/resources/application.yml`:

| Property | Default | Description |
|---|---|---|
| `github.token` | *(required)* | GitHub PAT — set via `GITHUB_TOKEN` env var |
| `github.api-base-url` | `https://api.github.com` | Override for GitHub Enterprise Server |
| `github.parallelism` | `10` | Number of threads for concurrent collaborator fetching |
| `github.page-size` | `100` | Results per page for paginated API calls (GitHub max is 100) |

**GitHub Enterprise Server support:**
```yaml
github:
  api-base-url: https://your-github-enterprise-hostname/api/v3
```

---

## Design Decisions

### 1. Parallel collaborator fetching
Each repository requires a separate GitHub API call to fetch its collaborators. For an org with 100 repos, doing this serially would be very slow. The service uses a `ExecutorService` with a bounded thread pool (default: 10) to run all collaborator lookups concurrently, then merges the results.

### 2. Full pagination
GitHub's API returns a maximum of 100 results per page. The `GitHubApiClient` automatically follows the `Link: rel="next"` response header on every request until all pages are consumed. This ensures no repositories or users are ever missed.

### 3. In-memory caching
Results from the GitHub API are cached in Caffeine with a 10-minute TTL. This prevents hammering the API if the same endpoint is called multiple times in quick succession — important given GitHub's rate limits (5,000 requests/hour for authenticated users).

### 4. Affiliation=all for collaborators
The collaborators endpoint is called with `?affiliation=all`, which captures three types of access:
- **Direct** — users explicitly added to the repo
- **Team** — users who belong to a team that was granted repo access
- **Org** — users who have access via organization-level base permissions

### 5. Soft 403 handling per repo
If the token lacks permission to list collaborators on a specific repo (403), the service logs a warning and skips that repo rather than failing the entire request. An org-level 403 (can't list repos at all) is still treated as a fatal error.

### 6. Separation of models and DTOs
GitHub API response shapes (`model/`) are kept separate from the API response shapes we expose (`dto/`). This prevents internal GitHub API changes from leaking into our public contract and lets us reshape data freely.

---

## Error Handling

All errors are returned as consistent JSON:

```json
{
  "error": "Organization or resource not found",
  "status": 404,
  "timestamp": "2024-03-15T10:30:00Z"
}
```

| HTTP Status | Cause |
|---|---|
| `400 Bad Request` | Missing or blank org name |
| `401 Unauthorized` | GitHub token is invalid or expired |
| `403 Forbidden` | Token lacks required org-level permissions |
| `404 Not Found` | Organization does not exist on GitHub |
| `429 Too Many Requests` | GitHub API rate limit exceeded |
| `500 Internal Server Error` | Unexpected application error |
