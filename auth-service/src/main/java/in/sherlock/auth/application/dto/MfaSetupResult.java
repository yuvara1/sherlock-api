package in.sherlock.auth.application.dto;

public record MfaSetupResult(String otpAuthUrl, String secret) {}
