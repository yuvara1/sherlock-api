package in.sherlock.auth.infrastructure.seed;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.sherlock.auth.application.port.out.UserRepositoryPort;
import java.nio.ByteBuffer;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevProfileSeedTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DemoDataSeeder seeder;
    @Autowired UserRepositoryPort users;
    @Autowired TransactionTemplate transactions;

    @Test
    void seededAdminAndDemoUsersCanLogIn() throws Exception {
        for (String[] creds : new String[][] {
                {"admin@sherlock.dev", "Sherlock@Owner123", "OWNER"},
                {"demo@sherlock.dev", "Sherlock@Guest123", "DEVELOPER"}}) {
            String body = mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", creds[0], "password", creds[1]))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mfaPending").value(false))
                    .andExpect(jsonPath("$.user.role").value(creds[2]))
                    .andExpect(jsonPath("$.tokens.accessToken", not(blankOrNullString())))
                    .andReturn().getResponse().getContentAsString();
            String access = json.readTree(body).at("/tokens/accessToken").asText();
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(creds[0]));
        }
    }

    @Test
    void seededMfaUserGetsChallengeAndCompletesWithFixedSecret() throws Exception {
        String challenge = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "mfa@sherlock.dev", "password", "Sherlock@Mfa1234"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mfaPending").value(true))
                .andExpect(jsonPath("$.mfaToken", not(blankOrNullString())))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/auth/login/mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "mfaToken", json.readTree(challenge).get("mfaToken").asText(),
                                "code", totp(DemoDataSeeder.MFA_SECRET)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mfaEnabled").value(true));
    }

    @Test
    void seederIsIdempotent() {
        transactions.executeWithoutResult(s -> seeder.seed());
        transactions.executeWithoutResult(s -> seeder.seed());
        org.junit.jupiter.api.Assertions.assertTrue(users.existsByEmail("admin@sherlock.dev"));
    }

    private static String totp(String base32) throws Exception {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream key = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : base32.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) { key.write((buffer >> (bits - 8)) & 0xff); bits -= 8; }
        }
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.toByteArray(), "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(java.time.Instant.now().getEpochSecond() / 30).array());
        int o = hash[hash.length - 1] & 0x0f;
        int bin = ((hash[o] & 0x7f) << 24) | ((hash[o + 1] & 0xff) << 16) | ((hash[o + 2] & 0xff) << 8) | (hash[o + 3] & 0xff);
        return "%06d".formatted(bin % 1_000_000);
    }
}
