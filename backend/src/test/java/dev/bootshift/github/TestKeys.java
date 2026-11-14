package dev.bootshift.github;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/** Throwaway RSA keys for tests, in the PEM formats GitHub and OpenSSL produce. Never real keys. */
final class TestKeys {

    private TestKeys() {
    }

    static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String pkcs8Pem(KeyPair keys) {
        return pem("PRIVATE KEY", keys.getPrivate().getEncoded());
    }

    /** GitHub's format: the RSAPrivateKey inside the PKCS#8 OCTET STRING. */
    static String pkcs1Pem(KeyPair keys) {
        return pem("RSA PRIVATE KEY", innerOctetString(keys.getPrivate().getEncoded()));
    }

    private static String pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    /** Walks PrivateKeyInfo: SEQUENCE { INTEGER, SEQUENCE, OCTET STRING } and returns the octets. */
    private static byte[] innerOctetString(byte[] der) {
        int[] pos = {0};
        expectTag(der, pos, 0x30);
        readLength(der, pos);
        skip(der, pos, 0x02);
        skip(der, pos, 0x30);
        expectTag(der, pos, 0x04);
        int length = readLength(der, pos);
        return Arrays.copyOfRange(der, pos[0], pos[0] + length);
    }

    private static void skip(byte[] der, int[] pos, int tag) {
        expectTag(der, pos, tag);
        int length = readLength(der, pos); // advances pos past the length bytes; read it before adding
        pos[0] += length;
    }

    private static void expectTag(byte[] der, int[] pos, int tag) {
        if ((der[pos[0]++] & 0xff) != tag) {
            throw new IllegalStateException("Unexpected DER tag");
        }
    }

    private static int readLength(byte[] der, int[] pos) {
        int first = der[pos[0]++] & 0xff;
        if (first < 0x80) {
            return first;
        }
        int length = 0;
        for (int i = 0; i < (first & 0x7f); i++) {
            length = (length << 8) | (der[pos[0]++] & 0xff);
        }
        return length;
    }
}
