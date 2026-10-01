package in.sherlock.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.sherlock.auth.application.port.out.PasswordResetNotifierPort;
import java.nio.ByteBuffer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "SERVICE_INTERNAL_TOKEN=test-service-token-at-least-32-characters")
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PasswordResetNotifierPort mailer;

    private String accessToken;
    private String refreshToken;
    private String email;
    private static final String PASSWORD = "CorrectHorse9!";

    @BeforeEach
    void register() throws Exception {
        email = "owner-" + java.util.UUID.randomUUID() + "@example.com";
        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Sherlock Owner",
                                  "email": "%s",
                                  "password": "%s",
                                  "organizationName": "Acme Reliability"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.tokens.accessToken", not(blankOrNullString())))
                .andReturn().getResponse().getContentAsString();
        JsonNode body = json.readTree(response);
        accessToken = body.at("/tokens/accessToken").asText();
        refreshToken = body.at("/tokens/refreshToken").asText();
    }

    @Test
    void workspaceIdentityIncludesOrganizationAndSessionStart() throws Exception {
        mvc.perform(get("/api/v1/auth/workspace").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization.name").value("Acme Reliability"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.sessionStartedAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.members[0].email").value(email));
    }

    @Test
    void membershipChangesRequireServiceSecretAndRevokeSessions() throws Exception {
        JsonNode workspace = json.readTree(mvc.perform(get("/api/v1/auth/workspace").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String path = "/internal/project/users/" + workspace.at("/user/id").asText() + "/membership";
        String body = "{\"organizationId\":\"" + workspace.at("/organization/id").asText() + "\",\"role\":\"VIEWER\",\"revoked\":true}";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("X-Internal-Token", "test-service-token-at-least-32-characters").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/workspace").header("Authorization", "Bearer " + accessToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refreshToken + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanReadProfileAndRotateRefreshToken() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sherlock Owner"));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(refreshToken)));
    }

    @Test
    void rejectsMissingAuthenticationAndWeakRegistrationPasswords() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Test User",
                                  "email": "weak@example.com",
                                  "password": "weakpassword",
                                  "organizationName": "Test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    void publishesRs256VerificationKey() throws Exception {
        mvc.perform(get("/api/v1/auth/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").value("sherlock-test-rs256-1"))
                .andExpect(jsonPath("$.keys[0].n", not(blankOrNullString())))
                .andExpect(jsonPath("$.keys[0].e", not(blankOrNullString())));
    }

    @Test
    void refreshTokensAreSingleUse() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void forgotPasswordDoesNotRevealWhetherAccountExists() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If the account exists, a password reset email has been sent."));
    }

    @Test
    void loginLogoutAndPasswordResetLifecycleWorks() throws Exception {
        String loginBody = json.writeValueAsString(java.util.Map.of("email", email, "password", PASSWORD));
        String loginResponse = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaPending").value(false))
                .andReturn().getResponse().getContentAsString();
        String loginAccess = json.readTree(loginResponse).at("/tokens/accessToken").asText();

        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + loginAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + loginAccess))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email))))
                .andExpect(status().isOk());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(anyString(), token.capture());

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "token", token.getValue(), "newPassword", "EvenStronger8!Pass"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "token", token.getValue(), "newPassword", "AnotherStrong7!Pass"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void mfaSetupAndChallengeLifecycleWorks() throws Exception {
        String setup = mvc.perform(post("/api/v1/auth/mfa/enable")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpAuthUrl", not(blankOrNullString())))
                .andReturn().getResponse().getContentAsString();
        String secret = json.readTree(setup).get("secret").asText();
        String code = currentTotp(secret);

        mvc.perform(post("/api/v1/auth/mfa/verify")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("code", code))))
                .andExpect(status().isNoContent());

        String challenge = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mfaPending").value(true))
                .andReturn().getResponse().getContentAsString();
        String mfaToken = json.readTree(challenge).get("mfaToken").asText();
        mvc.perform(post("/api/v1/auth/login/mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "mfaToken", mfaToken, "code", currentTotp(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokens.accessToken", not(blankOrNullString())));
    }

    private static String currentTotp(String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(8)
                .putLong(java.time.Instant.now().getEpochSecond() / 30).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        return "%06d".formatted(binary % 1_000_000);
    }

    private static byte[] decodeBase32(String value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        int buffer = 0;
        int bits = 0;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        for (char character : value.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(character);
            bits += 5;
            if (bits >= 8) {
                output.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return output.toByteArray();
    }
}
