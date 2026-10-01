package in.sherlock.auth.application.dto;

public record TokensResult(String accessToken, String refreshToken, long expiresIn) {}
