package in.sherlock.auth.application.port.out;

import java.util.Map;

public interface PublicKeyProviderPort {
    Map<String, Object> jwk();
}
