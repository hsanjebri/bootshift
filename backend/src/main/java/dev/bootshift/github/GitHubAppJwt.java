package dev.bootshift.github;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

/**
 * Signs the short-lived JWT a GitHub App uses to call app-level endpoints (RS256, valid for under
 * ten minutes, backdated 60 seconds for clock drift, as GitHub's docs recommend).
 *
 * <p>GitHub generates PKCS#1 keys ({@code BEGIN RSA PRIVATE KEY}); the JDK reads PKCS#8, so a
 * PKCS#1 key is wrapped in a PKCS#8 structure first. PKCS#8 keys are accepted as they are.
 */
public final class GitHubAppJwt {

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    /** DER of AlgorithmIdentifier { rsaEncryption, NULL }. */
    private static final byte[] RSA_ALGORITHM = {
        0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
    };

    private final long appId;
    private final PrivateKey key;
    private final Clock clock;

    public GitHubAppJwt(long appId, PrivateKey key, Clock clock) {
        this.appId = appId;
        this.key = key;
        this.clock = clock;
    }

    public static GitHubAppJwt fromPemFile(long appId, Path pem, Clock clock) {
        try {
            return new GitHubAppJwt(appId, readPrivateKey(Files.readString(pem)), clock);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read GitHub App private key " + pem, e);
        }
    }

    /** A fresh JWT, valid for nine minutes. */
    public String create() {
        long now = Instant.now(clock).getEpochSecond();
        String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"iat\":" + (now - 60) + ",\"exp\":" + (now + 540) + ",\"iss\":\"" + appId + "\"}");
        String signingInput = header + "." + payload;
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + URL.encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot sign GitHub App JWT", e);
        }
    }

    static PrivateKey readPrivateKey(String pem) {
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        if (!pkcs1 && !pem.contains("BEGIN PRIVATE KEY")) {
            throw new IllegalArgumentException("Not a PEM private key");
        }
        String base64 = pem.replaceAll("-----(BEGIN|END) (RSA )?PRIVATE KEY-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs1 ? wrapPkcs1(der) : der));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid RSA private key", e);
        }
    }

    /** PrivateKeyInfo ::= SEQUENCE { INTEGER 0, AlgorithmIdentifier, OCTET STRING pkcs1 }. */
    static byte[] wrapPkcs1(byte[] pkcs1) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(new byte[] {0x02, 0x01, 0x00});
        body.writeBytes(RSA_ALGORITHM);
        body.write(0x04);
        writeLength(body, pkcs1.length);
        body.writeBytes(pkcs1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x30);
        writeLength(out, body.size());
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream out, int length) {
        if (length < 0x80) {
            out.write(length);
            return;
        }
        int bytes = length > 0xffffff ? 4 : length > 0xffff ? 3 : length > 0xff ? 2 : 1;
        out.write(0x80 | bytes);
        for (int i = bytes - 1; i >= 0; i--) {
            out.write((length >>> (8 * i)) & 0xff);
        }
    }

    private static String encode(String json) {
        return URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
