package in.sherlock.auth.application.port.in;

import in.sherlock.auth.application.dto.*;
import java.util.List;
import java.util.UUID;

public interface PasswordResetUseCase {
    void forgotPassword(String email, RequestContext context);

    void resetPassword(String token, String newPassword, RequestContext context);
}
