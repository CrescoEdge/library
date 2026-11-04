package io.cresco.library.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertPath;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Verifies that a jar arriving over the network is signed, entirely, by a signer that chains to a configured trust
 * store, and computes its SHA-384 itself. A hash a caller supplies is advisory only.
 *
 * <p>A jar is accepted only if:
 * <ul>
 *   <li>it is a regular file (not a symlink) and a well-formed zip whose central directory and local headers name the
 *       same entries, each exactly once (no duplicate or hidden entries that one zip reader sees and another does not);</li>
 *   <li>it has a manifest, and every entry other than a directory and the signature files themselves
 *       ({@code META-INF/MANIFEST.MF}, {@code META-INF/*.SF|.RSA|.DSA|.EC}, {@code META-INF/SIG-*}) verifies against its
 *       manifest digest and carries a code signer (the JDK's verifier treats a disabled algorithm as unsigned);</li>
 *   <li>for every such entry, at least one of its signers has a certificate path that validates (PKIX, in date) to a
 *       trusted certificate in {@code trust}, and whose leaf, if it carries key usage or extended key usage, permits
 *       code signing.</li>
 * </ul>
 * An unsigned jar, a partially signed jar (any unsigned or tampered entry), and a jar signed by a foreign signer are
 * refused.
 *
 * <p>A signature carrying a trusted RFC 3161 timestamp (the TSA's certificate has the time-stamping extended key usage
 * and chains, valid at the stamped time, to the same trust store) is judged at the stamped time: a jar signed while its
 * certificate was valid stays installable after that certificate expires. Without a timestamp (or with an untrusted one)
 * the signer must be valid now -- re-sign before the signing certificate expires.
 *
 * <p>The verified bytes are the file's bytes at the time of the call. Callers install from a private copy that nobody
 * else can write, so the file cannot change between this check and its use.
 */
public final class JarSignatures {

    /** Extended key usage: code signing. */
    private static final String EKU_CODE_SIGNING = "1.3.6.1.5.5.7.3.3";
    /** Extended key usage: any. */
    private static final String EKU_ANY = "2.5.29.37.0";
    /** Upper bound on entries, to bound the work an attacker-supplied jar can cause. */
    private static final int MAX_ENTRIES = 200_000;

    private JarSignatures() {}

    /** Why a jar was refused. */
    public static final class Rejected extends GeneralSecurityException {
        private static final long serialVersionUID = 1L;
        public Rejected(String message) { super(message); }
        public Rejected(String message, Throwable cause) { super(message, cause); }
    }

    /**
     * Verify {@code jar} against {@code trust} and return its SHA-384, lowercase hex.
     *
     * @throws Rejected                 the jar is unsigned, partially signed, tampered, signed by an untrusted signer,
     *                                  malformed, or {@code trust} holds no trusted certificate
     * @throws IOException              the jar could not be read
     * @throws GeneralSecurityException the trust store could not be read
     */
    public static String verify(Path jar, KeyStore trust) throws IOException, GeneralSecurityException {
        if (jar == null) throw new Rejected("no jar");
        if (trust == null) throw new Rejected("no plugin signing trust store is configured");
        List<X509Certificate> anchors = trustedCertificates(trust);
        if (anchors.isEmpty()) throw new Rejected("the plugin signing trust store holds no trusted certificate");
        if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)) throw new Rejected(jar + " is not a regular file");

        Set<String> localNames = localHeaderNames(jar);

        int signedEntries = 0;
        Set<String> centralNames = new HashSet<>();
        try (JarFile jf = new JarFile(jar.toFile(), true)) {
            if (jf.getManifest() == null) throw new Rejected(jar.getFileName() + " has no manifest, so it is not signed");
            byte[] buf = new byte[65536];
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (!centralNames.add(name)) throw new Rejected("duplicate entry " + name);
                if (centralNames.size() > MAX_ENTRIES) throw new Rejected("more than " + MAX_ENTRIES + " entries");
                // Reading the whole entry is what makes the JDK verify it against the manifest digest; a mismatch
                // throws SecurityException.
                try (InputStream in = jf.getInputStream(e)) {
                    while (in.read(buf) != -1) { /* drain */ }
                } catch (SecurityException se) {
                    throw new Rejected("entry " + name + " does not match its signed digest: " + se.getMessage(), se);
                }
                if (e.isDirectory() || isSignatureRelated(name)) continue;
                CodeSigner[] signers = e.getCodeSigners();
                if (signers == null || signers.length == 0) throw new Rejected("entry " + name + " is not signed");
                if (!anyTrusted(signers, anchors)) throw new Rejected("entry " + name + " is not signed by a trusted signer");
                signedEntries++;
            }
        } catch (SecurityException se) {
            throw new Rejected("the jar's signature is invalid: " + se.getMessage(), se);
        } catch (java.util.zip.ZipException ze) {
            throw new Rejected("not a valid jar: " + ze.getMessage(), ze);
        }
        if (signedEntries == 0) throw new Rejected(jar.getFileName() + " has no signed content");
        if (!centralNames.equals(localNames)) {
            throw new Rejected("the jar's local entries differ from its central directory");
        }
        return sha384Hex(jar);
    }

    /** SHA-384 of a file, lowercase hex. */
    public static String sha384Hex(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-384");
            byte[] buf = new byte[65536];
            try (InputStream in = Files.newInputStream(file)) {
                int n;
                while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            }
            return toHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException nsae) {
            throw new IllegalStateException("SHA-384 is unavailable", nsae);
        }
    }

    /**
     * Load a trust store for jar signatures: PKCS12 or JKS by content. Only trusted-certificate entries count as
     * anchors. Returns the store; throws if it cannot be read.
     */
    public static KeyStore loadTrustStore(Path path, char[] password) throws IOException, GeneralSecurityException {
        if (path == null || !Files.isRegularFile(path)) throw new IOException("no trust store at " + path);
        // a PEM bundle of trusted certificates needs no password (security_profile=production allows none)
        byte[] head = new byte[64];
        int n;
        try (InputStream in = Files.newInputStream(path)) { n = in.readNBytes(head, 0, head.length); }
        if (new String(head, 0, Math.max(0, n), java.nio.charset.StandardCharsets.US_ASCII).trim().startsWith("-----BEGIN")) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            int i = 0;
            try (InputStream in = Files.newInputStream(path)) {
                for (java.security.cert.Certificate c : java.security.cert.CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                    ks.setCertificateEntry("anchor-" + (i++), c);
                }
            }
            if (i == 0) throw new IOException("no certificate in the PEM trust store " + path);
            return ks;
        }
        IOException last = null;
        for (String type : new String[]{"PKCS12", "JKS"}) {
            try (InputStream in = Files.newInputStream(path)) {
                KeyStore ks = KeyStore.getInstance(type);
                ks.load(in, password);
                return ks;
            } catch (IOException ioe) {
                last = ioe;
            }
        }
        throw last;
    }

    /** True for the files that carry a jar's signature rather than its content. */
    static boolean isSignatureRelated(String name) {
        String up = name.toUpperCase(Locale.ROOT);
        if (!up.startsWith("META-INF/")) return false;
        String rest = up.substring("META-INF/".length());
        if (rest.contains("/")) return false;
        return rest.equals("MANIFEST.MF") || rest.endsWith(".SF") || rest.endsWith(".RSA") || rest.endsWith(".DSA")
                || rest.endsWith(".EC") || rest.startsWith("SIG-");
    }

    private static boolean anyTrusted(CodeSigner[] signers, List<X509Certificate> anchors) {
        for (CodeSigner s : signers) {
            CertPath cp = s.getSignerCertPath();
            if (cp == null) continue;
            List<X509Certificate> chain = new ArrayList<>();
            for (Certificate c : cp.getCertificates()) {
                if (!(c instanceof X509Certificate)) { chain = null; break; }
                chain.add((X509Certificate) c);
            }
            if (chain == null || chain.isEmpty()) continue;
            if (!permitsCodeSigning(chain.get(0))) continue;
            // a trusted RFC 3161 timestamp vouches the signature was made at its time: the signer is judged then, so a
            // jar stays installable after its signing certificate expires; without one, the signer is judged now
            java.util.Date at = trustedTimestamp(s.getTimestamp(), anchors);
            if (CertTrust.verifyChain(chain, anchors, at == null ? new java.util.Date() : at)) return true;
        }
        return false;
    }

    /** Extended key usage: time stamping (RFC 3161 TSA certificates). */
    static final String EKU_TIME_STAMPING = "1.3.6.1.5.5.7.3.8";
    /** How far in the future a timestamp may lie (clock skew between the TSA and this node). */
    static final long MAX_TIMESTAMP_SKEW_MS = 5 * 60 * 1000L;

    /**
     * The time a signature's RFC 3161 timestamp vouches for, or null when it has none or the timestamp is not trusted. The
     * JDK has checked the token's signature and that it covers this signature; here the TSA's certificate must carry the
     * time-stamping extended key usage and chain, valid at the stamped time, to a certificate of the same trust store, and
     * the time may not lie in the future.
     */
    static java.util.Date trustedTimestamp(java.security.Timestamp ts, List<X509Certificate> anchors) {
        if (ts == null || ts.getTimestamp() == null || ts.getSignerCertPath() == null) return null;
        java.util.Date at = ts.getTimestamp();
        if (at.getTime() > System.currentTimeMillis() + MAX_TIMESTAMP_SKEW_MS) return null;
        List<X509Certificate> tsa = new ArrayList<>();
        for (Certificate c : ts.getSignerCertPath().getCertificates()) {
            if (!(c instanceof X509Certificate)) return null;
            tsa.add((X509Certificate) c);
        }
        if (tsa.isEmpty()) return null;
        try {
            List<String> eku = tsa.get(0).getExtendedKeyUsage();
            if (eku == null || !eku.contains(EKU_TIME_STAMPING)) return null;
        } catch (java.security.cert.CertificateParsingException cpe) {
            return null;
        }
        return CertTrust.verifyChain(tsa, anchors, at) ? at : null;
    }

    private static boolean permitsCodeSigning(X509Certificate leaf) {
        boolean[] ku = leaf.getKeyUsage();
        if (ku != null && (ku.length == 0 || !ku[0])) return false; // digitalSignature
        try {
            List<String> eku = leaf.getExtendedKeyUsage();
            if (eku != null && !eku.contains(EKU_CODE_SIGNING) && !eku.contains(EKU_ANY)) return false;
        } catch (java.security.cert.CertificateParsingException cpe) {
            return false;
        }
        return true;
    }

    private static List<X509Certificate> trustedCertificates(KeyStore ks) throws GeneralSecurityException {
        List<X509Certificate> out = new ArrayList<>();
        for (String alias : Collections.list(ks.aliases())) {
            if (!ks.isCertificateEntry(alias)) continue;
            Certificate c = ks.getCertificate(alias);
            if (c instanceof X509Certificate) out.add((X509Certificate) c);
        }
        return out;
    }

    /** Entry names as the local file headers give them, refusing duplicates. */
    private static Set<String> localHeaderNames(Path jar) throws IOException, Rejected {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                if (!names.add(ze.getName())) throw new Rejected("duplicate entry " + ze.getName());
                if (names.size() > MAX_ENTRIES) throw new Rejected("more than " + MAX_ENTRIES + " entries");
            }
        } catch (java.util.zip.ZipException ze) {
            throw new Rejected("not a valid jar: " + ze.getMessage(), ze);
        }
        return names;
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
