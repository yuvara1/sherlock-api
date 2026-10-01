package in.sherlock.auth.application.port.in;

import in.sherlock.auth.application.dto.*;
import java.util.List;
import java.util.UUID;

public interface LoginUseCase {
    LoginResult login(LoginCommand command, RequestContext context);

    LoginResult completeMfaLogin(MfaLoginCommand command, RequestContext context);
}
