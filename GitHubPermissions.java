package com.cloudeagle.githubaccess.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubPermissions {
    private boolean admin;
    private boolean maintain;
    private boolean push;
    private boolean triage;
    private boolean pull;
}
