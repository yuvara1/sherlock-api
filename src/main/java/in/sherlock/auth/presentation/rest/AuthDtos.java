package in.sherlock.auth.presentation.rest;

import in.sherlock.auth.application.dto.*;
import in.sherlock.auth.domain.model.AuditEvent;
import in.sherlock.auth.domain.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** HTTP request/response contracts. Field names are part of the public API used by the frontend. */
public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 80) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotBlank @Size(min = 2, max = 100) String organizationName) {
        RegisterCommand toCommand() { return new RegisterCommand(name, email, password, organizationName); }
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {
        LoginCommand toCommand() { return new LoginCommand(email, password); }
    }

    public record MfaLoginRequest(
            @NotBlank String mfaToken,
            @NotBlank @Pattern(regexp = "\\d{6}") String code) {
        MfaLoginCommand toCommand() { return new MfaLoginCommand(mfaToken, code); }
    }

    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record LogoutRequest(String refreshToken) {}
    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {}
    public record MfaCodeRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {}
    public record MfaDisableRequest(
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record UserResponse(
            UUID id, String email, String name, Role role, UUID organizationId,
            boolean mfaEnabled, String avatarUrl, Instant createdAt) {
        static UserResponse from(UserResult user) {
            return user == null ? null : new UserResponse(user.id(), user.email(), user.name(), user.role(),
                    user.organizationId(), user.mfaEnabled(), user.avatarUrl(), user.createdAt());
        }
    }

    public record OrganizationResponse(UUID id, String name, String slug, String plan, Instant createdAt) {
        static OrganizationResponse from(OrganizationResult org) {
            return new OrganizationResponse(org.id(), org.name(), org.slug(), org.plan(), org.createdAt());
        }
    }

    public record TokensResponse(String accessToken, String refreshToken, long expiresIn) {
        static TokensResponse from(TokensResult tokens) {
            return tokens == null ? null
                    : new TokensResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
        }
    }

    public record AuthResponse(UserResponse user, TokensResponse tokens, OrganizationResponse organization) {
        static AuthResponse from(AuthResult result) {
            return new AuthResponse(UserResponse.from(result.user()), TokensResponse.from(result.tokens()),
                    OrganizationResponse.from(result.organization()));
        }
    }

    public record LoginResponse(UserResponse user, TokensResponse tokens, boolean mfaPending, String mfaToken) {
        static LoginResponse from(LoginResult result) {
            return new LoginResponse(UserResponse.from(result.user()), TokensResponse.from(result.tokens()),
                    result.mfaPending(), result.mfaToken());
        }
    }

    public record MessageResponse(String message) {}

    public record MfaSetupResponse(String otpAuthUrl, String secret) {
        static MfaSetupResponse from(MfaSetupResult result) {
            return new MfaSetupResponse(result.otpAuthUrl(), result.secret());
        }
    }

    public record SessionResponse(
            UUID id, String deviceInfo, String ipAddress, Instant createdAt, Instant expiresAt, boolean current) {
        static SessionResponse from(SessionResult s) {
            return new SessionResponse(s.id(), s.deviceInfo(), s.ipAddress(), s.createdAt(), s.expiresAt(), s.current());
        }
    }

    public record AuditResponse(UUID id, AuditEvent event, String ipAddress, String userAgent, Instant timestamp) {
        static AuditResponse from(AuditResult a) {
            return new AuditResponse(a.id(), a.event(), a.ipAddress(), a.userAgent(), a.timestamp());
        }
    }

    public record PageResponse<T>(List<T> data, long total, int page, int pageSize, boolean hasMore) {
        static <S, T> PageResponse<T> from(PageResult<S> page, java.util.function.Function<S, T> mapper) {
            return new PageResponse<>(page.data().stream().map(mapper).toList(), page.total(), page.page(),
                    page.pageSize(), page.hasMore());
        }
    }
}
