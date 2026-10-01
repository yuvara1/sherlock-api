package in.sherlock.auth.infrastructure.mail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class ResetLinks {
    private ResetLinks() {}

    static String build(String baseUrl, String token) {
        return baseUrl + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
