package in.sherlock.auth.presentation.rest;

import in.sherlock.auth.application.port.out.PublicKeyProviderPort;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/.well-known")
public class JwkController {
    private final PublicKeyProviderPort publicKeys;

    public JwkController(PublicKeyProviderPort publicKeys) {
        this.publicKeys = publicKeys;
    }

    @GetMapping("/jwks.json")
    Map<String, Object> jwks() {
        return Map.of("keys", List.of(publicKeys.jwk()));
    }
}
