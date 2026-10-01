package in.sherlock.auth.domain.model;

public enum AuditEvent {
    REGISTER,
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    TOKEN_REFRESH,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET,
    MFA_ENABLED,
    MFA_DISABLED,
    SESSION_REVOKED
}
