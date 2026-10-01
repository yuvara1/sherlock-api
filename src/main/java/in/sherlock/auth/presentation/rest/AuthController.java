package in.sherlock.auth.presentation.rest;

import static in.sherlock.auth.presentation.rest.AuthDtos.*;

import in.sherlock.auth.application.dto.RequestContext;
import in.sherlock.auth.application.port.in.*;
import in.sherlock.auth.infrastructure.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegisterUseCase registration;
    private final LoginUseCase login;
    private final TokenRefreshUseCase tokenRefresh;
    private final SessionUseCase sessions;
    private final PasswordResetUseCase passwordReset;
    private final MfaUseCase mfa;
    private final UserQueryUseCase users;
    private final AuditQueryUseCase audit;

    public AuthController(RegisterUseCase registration, LoginUseCase login, TokenRefreshUseCase tokenRefresh,
            SessionUseCase sessions, PasswordResetUseCase passwordReset, MfaUseCase mfa,
            UserQueryUseCase users, AuditQueryUseCase audit) {
        this.registration = registration;
        this.login = login;
        this.tokenRefresh = tokenRefresh;
        this.sessions = sessions;
        this.passwordReset = passwordReset;
        this.mfa = mfa;
        this.users = users;
        this.audit = audit;
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        AuthResponse response = AuthResponse.from(registration.register(request.toCommand(), context(http)));
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.user().id())).body(response);
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        LoginResponse response = LoginResponse.from(login.login(request.toCommand(), context(http)));
        return ResponseEntity.status(response.mfaPending() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(response);
    }

    @PostMapping("/login/mfa")
    LoginResponse mfaLogin(@Valid @RequestBody MfaLoginRequest request, HttpServletRequest http) {
        return LoginResponse.from(login.completeMfaLogin(request.toCommand(), context(http)));
    }

    @PostMapping("/refresh")
    TokensResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return TokensResponse.from(tokenRefresh.refresh(request.refreshToken(), context(http)));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody(required = false) LogoutRequest request, HttpServletRequest http) {
        sessions.logout(principal.userId(), principal.sessionId(), context(http));
    }

    @PostMapping("/forgot-password")
    MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        passwordReset.forgotPassword(request.email(), context(http));
        return new MessageResponse("If the account exists, a password reset email has been sent.");
    }

    @PostMapping("/reset-password")
    MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        passwordReset.resetPassword(request.token(), request.newPassword(), context(http));
        return new MessageResponse("Your password has been reset. Sign in with the new password.");
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return UserResponse.from(users.me(principal.userId()));
    }

    @GetMapping("/sessions")
    List<SessionResponse> sessions(@AuthenticationPrincipal AuthPrincipal principal) {
        return sessions.sessions(principal.userId(), principal.sessionId()).stream()
                .map(SessionResponse::from).toList();
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSession(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID sessionId,
            HttpServletRequest http) {
        sessions.revokeSession(principal.userId(), sessionId, context(http));
    }

    @GetMapping("/audit-log")
    PageResponse<AuditResponse> auditLog(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int pageSize) {
        return PageResponse.from(audit.auditLog(principal.userId(), page, pageSize), AuditResponse::from);
    }

    @PostMapping("/mfa/enable")
    MfaSetupResponse beginMfa(@AuthenticationPrincipal AuthPrincipal principal) {
        return MfaSetupResponse.from(mfa.beginMfa(principal.userId()));
    }

    @PostMapping("/mfa/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyMfa(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody MfaCodeRequest request, HttpServletRequest http) {
        mfa.verifyMfa(principal.userId(), request.code(), context(http));
    }

    @DeleteMapping("/mfa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disableMfa(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody MfaDisableRequest request, HttpServletRequest http) {
        mfa.disableMfa(principal.userId(), request.password(), request.code(), context(http));
    }

    private static RequestContext context(HttpServletRequest request) {
        return new RequestContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
