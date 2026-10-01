package in.sherlock.auth.application.dto;

public record RegisterCommand(String name, String email, String password, String organizationName) {}
