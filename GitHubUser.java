package com.cloudeagle.githubaccess.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubUser {
    private Long id;
    private String login;
    @JsonProperty("avatar_url") private String avatarUrl;
    @JsonProperty("html_url") private String htmlUrl;
    private String type;
    @JsonProperty("role_name") private String roleName;
    private GitHubPermissions permissions;
}
