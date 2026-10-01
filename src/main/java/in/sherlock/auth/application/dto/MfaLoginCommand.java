package in.sherlock.auth.application.dto;

public record MfaLoginCommand(String mfaToken, String code) {}
