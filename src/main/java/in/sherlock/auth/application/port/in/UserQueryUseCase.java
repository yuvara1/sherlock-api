package in.sherlock.auth.application.port.in;

import in.sherlock.auth.application.dto.*;
import java.util.List;
import java.util.UUID;

public interface UserQueryUseCase {
    UserResult me(UUID userId);
}
