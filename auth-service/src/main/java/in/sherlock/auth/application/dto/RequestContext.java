package in.sherlock.auth.application.dto;

public record RequestContext(String ipAddress, String userAgent) {
    public RequestContext {
        ipAddress = clean(ipAddress, 64, "unknown");
        userAgent = clean(userAgent, 500, "unknown");
    }

    private static String clean(String value, int max, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String cleaned = value.replaceAll("[\\r\\n\\u0000]", "");
        return cleaned.substring(0, Math.min(cleaned.length(), max));
    }
}
