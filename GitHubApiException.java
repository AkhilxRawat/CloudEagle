package com.cloudeagle.githubaccess.exception;

import org.springframework.http.HttpStatus;

public class GitHubApiException extends RuntimeException {
    private final HttpStatus status;

    public GitHubApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public GitHubApiException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
