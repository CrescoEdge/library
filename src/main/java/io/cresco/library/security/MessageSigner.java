package io.cresco.library.security;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * End-to-end message authentication that survives broker-to-broker relaying. Transport TLS protects a
 * single hop; a message that transits region&rarr;global&rarr;region crosses multiple broker trust
 * domains. Signing the payload with the sender's leaf private key lets any receiver prove the origin
 * identity <em>and</em> that the bytes were not tampered with, independent of how many brokers relayed
 * it — the receiver verifies with the sender's leaf certificate (chain-validated to a trusted region CA
 * via {@link CertTrust}).
 *
 * <p>Signing is deliberately <em>selective</em> (mutual-TLS is the default per-hop protection; this is
 * the elevated tier for cross-domain or sensitive traffic), so this is a small stateless primitive that
 * callers invoke per message. RSA certs (Cresco's current chain) use {@code SHA256withRSA}; the JCA
 * picks the matching algorithm for EC keys if the fabric later moves to ECDSA. Pure JDK — no provider
 * dependency — so it is callable from any plugin through the shared library.
 */
public final class MessageSigner {

    private MessageSigner() {}

    private static String algorithmFor(String keyAlgorithm) {
        if (keyAlgorithm == null) {
            return "SHA256withRSA";
        }
        String a = keyAlgorithm.toUpperCase();
        if (a.contains("EC")) {
            return "SHA256withECDSA";
        }
        if (a.contains("DSA")) {
            return "SHA256withDSA";
        }
        return "SHA256withRSA";
    }

    /** Sign {@code data} with {@code key}. Returns the raw signature, or {@code null} on failure. */
    public static byte[] sign(byte[] data, PrivateKey key) {
        if (data == null || key == null) {
            return null;
        }
        try {
            Signature sig = Signature.getInstance(algorithmFor(key.getAlgorithm()));
            sig.initSign(key);
            sig.update(data);
            return sig.sign();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Base64 convenience for stuffing a signature into a message parameter. */
    public static String signBase64(byte[] data, PrivateKey key) {
        byte[] s = sign(data, key);
        return (s == null) ? null : Base64.getEncoder().encodeToString(s);
    }

    /** Verify {@code data} against {@code signature} using {@code key}. */
    public static boolean verify(byte[] data, byte[] signature, PublicKey key) {
        if (data == null || signature == null || key == null) {
            return false;
        }
        try {
            Signature sig = Signature.getInstance(algorithmFor(key.getAlgorithm()));
            sig.initVerify(key);
            sig.update(data);
            return sig.verify(signature);
        } catch (Exception ex) {
            return false;
        }
    }

    /** Verify using the public key inside a sender's certificate. Chain-validate the cert separately. */
    public static boolean verify(byte[] data, byte[] signature, X509Certificate signerCert) {
        return signerCert != null && verify(data, signature, signerCert.getPublicKey());
    }

    /** Base64 convenience mirroring {@link #signBase64}. */
    public static boolean verifyBase64(byte[] data, String signatureBase64, X509Certificate signerCert) {
        if (signatureBase64 == null) {
            return false;
        }
        try {
            return verify(data, Base64.getDecoder().decode(signatureBase64), signerCert);
        } catch (Exception ex) {
            return false;
        }
    }
}
