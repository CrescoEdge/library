package io.cresco.library.crypto;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Single crypto seam for the whole framework — the one place algorithms and the security provider
 * are chosen, so the fabric can be moved from "approved algorithms" to a FIPS-140-3-validated module
 * (BouncyCastle-FIPS / JDK FIPS mode), and later to CNSA 2.0 post-quantum, by changing this class and
 * the installed provider only, not the call sites.
 *
 * <p>Policy constants below are chosen to be simultaneously FIPS-approved AND CNSA-1.0-strength, so
 * ratcheting the deployment from CUI/Moderate up to IL5+/NSS is a configuration change, not a rewrite:
 * SHA-256 for bulk content integrity, SHA-384 for security-relevant digests, PBKDF2-HMAC-SHA-384 for
 * key derivation from operator secrets, AES-256, RSA-3072 / EC P-384, DRBG. No MD5, SHA-1, or ECB.
 *
 * <p>Pure JDK today (the SUN/SunJCE providers service these algorithms); when the validated provider
 * is installed and the JVM is in approved-only mode, the same {@code getInstance} calls route through
 * it. See {@code Compliance_Roadmap_800-53_CMMC.md} (Phase 0) and {@code POAM} line P0-BCFIPS.
 */
public final class CrescoCrypto {

    // ---- Algorithm policy (FIPS-approved AND CNSA-1.0-strength) ----
    /** Bulk file/transfer integrity hash (was MD5 across the fabric). */
    public static final String CONTENT_HASH_ALG = "SHA-256";
    /** Security-relevant digest (identity/audit). */
    public static final String SEC_HASH_ALG = "SHA-384";
    /** Key derivation from an operator passphrase/secret. PBKDF2 is FIPS-approved; Argon2 is not. */
    public static final String KDF_ALG = "PBKDF2WithHmacSHA384";
    /** PBKDF2 work factor. Raise as hardware improves; a change here is protocol-compatible. */
    public static final int KDF_ITERATIONS = 210_000;
    public static final int KDF_KEY_BITS = 256;
    /** Keystore container: PKCS12, never JKS (JKS uses SHA-1 integrity and weak key protection). */
    public static final String KEYSTORE_TYPE = "PKCS12";
    /** Signature suites (CNSA-1.0). */
    public static final String SIG_ALG_RSA = "SHA384withRSA";
    public static final String SIG_ALG_EC = "SHA384withECDSA";
    /** RSA floor (CNSA-1.0). The old code clamped to a factorable 512 bits. */
    public static final int MIN_RSA_KEY_BITS = 3072;
    /** DRBG (NIST SP 800-90A); replaces the legacy SHA1PRNG. */
    public static final String RNG_ALG = "DRBG";

    private CrescoCrypto() {}

    /**
     * Name of the JCA provider backing framework crypto, or "" for the platform default. Set
     * {@code -Dcresco.crypto.provider=BCFIPS} once the validated module is installed; call sites do
     * not change. Exposed so a health/inventory check can report the active provider.
     */
    public static String providerName() {
        return System.getProperty("cresco.crypto.provider", "");
    }

    /** DRBG-backed SecureRandom; falls back to the JDK default (also DRBG on modern JVMs). */
    public static SecureRandom secureRandom() {
        try {
            return SecureRandom.getInstance(RNG_ALG);
        } catch (Exception ex) {
            return new SecureRandom();
        }
    }

    // ---- Content hashing (the single implementation; replaces every ad-hoc getMD5) ----

    /** Streaming content hash as lowercase hex. Reads and does NOT close the stream. 8KB buffer. */
    public static String contentHashHex(InputStream in) throws Exception {
        MessageDigest digest = md(CONTENT_HASH_ALG);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return toHex(digest.digest());
    }

    /** Content hash of a file path as lowercase hex. */
    public static String contentHashHex(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return contentHashHex(in);
        }
    }

    /** Content hash of a file path string as lowercase hex. */
    public static String contentHashHex(String path) throws Exception {
        return contentHashHex(Paths.get(path));
    }

    /** Digest of an in-memory buffer as lowercase hex, under the named approved algorithm. */
    public static String digestHex(byte[] data, String algorithm) throws Exception {
        return toHex(md(algorithm).digest(data));
    }

    // ---- Key derivation (PBKDF2-HMAC-SHA-384; replaces SHA-1 + static salt) ----

    /**
     * Derive a symmetric key from an operator secret with a random per-use salt. The salt is not a
     * secret and must be stored/transmitted alongside the derived-key consumer so it can re-derive.
     */
    public static byte[] deriveKey(char[] secret, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(secret, salt, KDF_ITERATIONS, KDF_KEY_BITS);
        try {
            SecretKeyFactory skf = provider().isEmpty()
                    ? SecretKeyFactory.getInstance(KDF_ALG)
                    : SecretKeyFactory.getInstance(KDF_ALG, provider());
            return skf.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static MessageDigest md(String alg) throws Exception {
        return provider().isEmpty()
                ? MessageDigest.getInstance(alg)
                : MessageDigest.getInstance(alg, provider());
    }

    private static String provider() {
        return providerName();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }
}
