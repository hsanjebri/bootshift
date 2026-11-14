package dev.bootshift.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GitHubAppJwtTest {

    private static final KeyPair KEYS = TestKeys.rsa();
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JsonMapper json = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"pkcs1", "pkcs8"})
    void readsBothPemFormatsGitHubAndOpenSslProduce(String format) throws Exception {
        String pem = format.equals("pkcs1") ? TestKeys.pkcs1Pem(KEYS) : TestKeys.pkcs8Pem(KEYS);

        String token = new GitHubAppJwt(12345, GitHubAppJwt.readPrivateKey(pem), CLOCK).create();

        assertThat(verifies(token)).isTrue();
    }

    @Test
    void claimsFollowGitHubRules() {
        String token = new GitHubAppJwt(12345, KEYS.getPrivate(), CLOCK).create();

        JsonNode header = decode(token.split("\\.")[0]);
        JsonNode claims = decode(token.split("\\.")[1]);
        assertThat(header.get("alg").asString()).isEqualTo("RS256");
        assertThat(claims.get("iss").asString()).isEqualTo("12345");
        // Backdated for clock drift, and valid for less than GitHub's ten-minute maximum.
        assertThat(claims.get("iat").asLong()).isEqualTo(NOW.getEpochSecond() - 60);
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isLessThanOrEqualTo(600);
    }

    @Test
    void rejectsSomethingThatIsNotAPrivateKey() {
        assertThatIllegalArgumentException().isThrownBy(() -> GitHubAppJwt.readPrivateKey("-----BEGIN CERTIFICATE-----"));
    }

    private boolean verifies(String token) throws Exception {
        String[] parts = token.split("\\.");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(KEYS.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
    }

    private JsonNode decode(String part) {
        return json.readTree(new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8));
    }
}
