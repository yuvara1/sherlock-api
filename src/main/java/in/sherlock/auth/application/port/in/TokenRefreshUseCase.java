package in.sherlock.auth.application.port.in;

import in.sherlock.auth.application.dto.*;
import java.util.List;
import java.util.UUID;

public interface TokenRefreshUseCase {
    TokensResult refresh(String refreshToken, RequestContext context);
}
