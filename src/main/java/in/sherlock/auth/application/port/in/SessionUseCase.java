package in.sherlock.auth.application.port.in;

import in.sherlock.auth.application.dto.*;
import java.util.List;
import java.util.UUID;

public interface SessionUseCase {
    void logout(UUID userId, UUID sessionId, RequestContext context);

    List<SessionResult> sessions(UUID userId, UUID currentSessionId);

    void revokeSession(UUID userId, UUID sessionId, RequestContext context);
}
