package in.sherlock.auth.application.dto;

import in.sherlock.auth.domain.model.UserAccount;

public record LoginResult(UserResult user, TokensResult tokens, boolean mfaPending, String mfaToken) {
    public static LoginResult authenticated(UserAccount user, TokensResult tokens) {
        return new LoginResult(UserResult.from(user), tokens, false, null);
    }

    public static LoginResult mfaPending(String token) {
        return new LoginResult(null, null, true, token);
    }
}
