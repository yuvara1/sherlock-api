package in.sherlock.auth.application.dto;

public record AuthResult(UserResult user, TokensResult tokens, OrganizationResult organization) {}
