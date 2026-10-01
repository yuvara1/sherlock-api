package in.sherlock.auth.application.port.in;

import in.sherlock.auth.application.dto.*;
import java.util.List;
import java.util.UUID;

public interface MfaUseCase {
    MfaSetupResult beginMfa(UUID userId);

    void verifyMfa(UUID userId, String code, RequestContext context);

    void disableMfa(UUID userId, String password, String code, RequestContext context);
}
