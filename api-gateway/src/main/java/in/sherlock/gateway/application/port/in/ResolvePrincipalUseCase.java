package in.sherlock.gateway.application.port.in;

import in.sherlock.gateway.domain.model.GatewayPrincipal;
import java.util.Map;

public interface ResolvePrincipalUseCase {
    GatewayPrincipal resolve(Map<String, Object> claims);
}
