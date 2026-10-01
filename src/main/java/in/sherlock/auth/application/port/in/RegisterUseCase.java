package in.sherlock.auth.application.port.in;

import in.sherlock.auth.application.dto.*;
import java.util.List;
import java.util.UUID;

public interface RegisterUseCase {
    AuthResult register(RegisterCommand command, RequestContext context);
}
