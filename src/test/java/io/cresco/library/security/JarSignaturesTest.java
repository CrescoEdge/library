package io.cresco.library.security;

import jdk.security.jarsigner.JarSigner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JarSignatures: a jar signed by a signer chaining to the trust store is accepted and its SHA-384 returned; unsigned,
 * partially signed, tampered and foreign-signed jars are refused. Every key is generated here, at test time, by the
 * JDK's own keytool; no key material is kept in the repository.
 */
class JarSignaturesTest {

    @TempDir
    static Path dir;

    private static final char[] PW = "test-only-not-a-secret".toCharArray();
    private static Path keys;          // CA + leaf (signed by CA) + foreign self-signed + leaf with server-only EKU
    private static KeyStore trust;     // the CA cert only
    private static KeyStore leafTrust; // the leaf cert pinned directly

    @BeforeAll
    static void makeKeys() throws Exception {
        keys = dir.resolve("keys.p12");
        keytool("-genkeypair", "-alias", "ca", "-keyalg", "EC", "-groupname", "secp384r1", "-dname", "CN=Test Signing CA",
                "-ext", "bc:c", "-validity", "2");
        keytool("-genkeypair", "-alias", "leaf", "-keyalg", "EC", "-groupname", "secp384r1", "-dname", "CN=Test Signer",
                "-validity", "2");
        Path csr = dir.resolve("leaf.csr"), crt = dir.resolve("leaf.crt");
        keytool("-certreq", "-alias", "leaf", "-file", csr.toString());
        keytool("-gencert", "-alias", "ca", "-infile", csr.toString(), "-outfile", crt.toString(), "-rfc", "-validity", "2",
                "-ext", "ku:c=digitalSignature", "-ext", "eku=codeSigning");
        // keytool builds the reply's chain from the CA already in the keystore
        keytool("-importcert", "-noprompt", "-alias", "leaf", "-file", crt.toString());

        keytool("-genkeypair", "-alias", "foreign", "-keyalg", "EC", "-groupname", "secp384r1", "-dname", "CN=Foreign",
                "-validity", "2");

        keytool("-genkeypair", "-alias", "tlsonly", "-keyalg", "EC", "-groupname", "secp384r1", "-dname", "CN=TLS Only",
                "-validity", "2");
        Path csr2 = dir.resolve("tls.csr"), crt2 = dir.resolve("tls.crt");
        keytool("-certreq", "-alias", "tlsonly", "-file", csr2.toString());
        keytool("-gencert", "-alias", "ca", "-infile", csr2.toString(), "-outfile", crt2.toString(), "-rfc", "-validity", "2",
                "-ext", "eku=serverAuth");
        keytool("-importcert", "-noprompt", "-alias", "tlsonly", "-file", crt2.toString());

        KeyStore ks = load();
        trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        trust.setCertificateEntry("ca", ks.getCertificate("ca"));
        leafTrust = KeyStore.getInstance("PKCS12");
        leafTrust.load(null, null);
        leafTrust.setCertificateEntry("pinned", ks.getCertificate("leaf"));
    }

    private static void keytool(String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        cmd.addAll(Arrays.asList(args));
        cmd.addAll(Arrays.asList("-keystore", keys.toString(), "-storetype", "PKCS12", "-storepass", new String(PW),
                "-keypass", new String(PW)));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "keytool timed out");
        assertEquals(0, p.exitValue(), "keytool " + String.join(" ", args) + ": " + out);
    }

    private static KeyStore load() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keys)) {
            ks.load(in, PW);
        }
        return ks;
    }

    private static Path plainJar(String name, String... entries) throws IOException {
        Path p = dir.resolve(name);
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().putValue("Bundle-SymbolicName", "test.bundle");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(p), mf)) {
            for (int i = 0; i < entries.length; i++) {
                out.putNextEntry(new JarEntry(entries[i]));
                out.write(("content of " + entries[i] + " #" + i).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return p;
    }

    private static Path sign(Path in, String alias, String outName) throws Exception {
        KeyStore ks = load();
        PrivateKey key = (PrivateKey) ks.getKey(alias, PW);
        Certificate[] chain = ks.getCertificateChain(alias);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        JarSigner signer = new JarSigner.Builder(key, cf.generateCertPath(Arrays.asList(chain)))
                .digestAlgorithm("SHA-384").signatureAlgorithm("SHA384withECDSA").build();
        Path out = dir.resolve(outName);
        try (ZipFile zf = new ZipFile(in.toFile()); OutputStream os = Files.newOutputStream(out)) {
            signer.sign(zf, os);
        }
        return out;
    }

    /** Copy a jar, adding (or replacing the bytes of) one entry after signing. */
    private static Path rewrite(Path in, String outName, String entry, byte[] bytes, boolean add) throws IOException {
        Path out = dir.resolve(outName);
        try (JarFile jf = new JarFile(in.toFile(), false); ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                zos.putNextEntry(new ZipEntry(e.getName()));
                if (!add && e.getName().equals(entry)) {
                    zos.write(bytes);
                } else {
                    try (InputStream is = jf.getInputStream(e)) { is.transferTo(zos); }
                }
                zos.closeEntry();
            }
            if (add) {
                zos.putNextEntry(new ZipEntry(entry));
                zos.write(bytes);
                zos.closeEntry();
            }
        }
        return out;
    }

    @Test
    void aJarSignedByATrustedSignerIsAcceptedAndItsSha384Returned() throws Exception {
        Path signed = sign(plainJar("a.jar", "io/x/A.class", "io/x/B.class", "META-INF/maven/x/pom.xml"), "leaf", "a-signed.jar");
        String sha = JarSignatures.verify(signed, trust);
        assertEquals(96, sha.length());
        assertEquals(JarSignatures.sha384Hex(signed), sha);
        assertTrue(sha.matches("[0-9a-f]{96}"));
    }

    /** A PEM bundle of trust anchors loads with no password (security_profile=production allows none). */
    @Test
    void aPemTrustBundleNeedsNoPassword() throws Exception {
        java.security.cert.Certificate ca = trust.getCertificate("ca");
        Path pem = dir.resolve("anchors.pem");
        Files.writeString(pem, "-----BEGIN CERTIFICATE-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(ca.getEncoded()) + "\n-----END CERTIFICATE-----\n");
        KeyStore fromPem = JarSignatures.loadTrustStore(pem, null);
        Path signed = sign(plainJar("pem.jar", "io/x/A.class"), "leaf", "pem-signed.jar");
        assertNotNull(JarSignatures.verify(signed, fromPem));
        Path empty = dir.resolve("empty.pem");
        Files.writeString(empty, "-----BEGIN NOTHING-----\n");
        assertThrows(Exception.class, () -> JarSignatures.loadTrustStore(empty, null));
    }

    @Test
    void aSignerPinnedDirectlyInTheTrustStoreIsAccepted() throws Exception {
        Path signed = sign(plainJar("p.jar", "io/x/A.class"), "leaf", "p-signed.jar");
        assertNotNull(JarSignatures.verify(signed, leafTrust));
    }

    @Test
    void anUnsignedJarIsRefused() throws Exception {
        Path plain = plainJar("u.jar", "io/x/A.class");
        JarSignatures.Rejected r = assertThrows(JarSignatures.Rejected.class, () -> JarSignatures.verify(plain, trust));
        assertTrue(r.getMessage().contains("not signed"), r.getMessage());
    }

    @Test
    void aJarWithAnEntryAddedAfterSigningIsRefused() throws Exception {
        Path signed = sign(plainJar("b.jar", "io/x/A.class"), "leaf", "b-signed.jar");
        Path partial = rewrite(signed, "b-partial.jar", "io/x/Evil.class", "evil".getBytes(StandardCharsets.UTF_8), true);
        JarSignatures.Rejected r = assertThrows(JarSignatures.Rejected.class, () -> JarSignatures.verify(partial, trust));
        assertTrue(r.getMessage().contains("io/x/Evil.class"), r.getMessage());
    }

    @Test
    void aJarWithAnEntryChangedAfterSigningIsRefused() throws Exception {
        Path signed = sign(plainJar("c.jar", "io/x/A.class", "io/x/B.class"), "leaf", "c-signed.jar");
        Path tampered = rewrite(signed, "c-tampered.jar", "io/x/B.class", "changed".getBytes(StandardCharsets.UTF_8), false);
        assertThrows(JarSignatures.Rejected.class, () -> JarSignatures.verify(tampered, trust));
    }

    @Test
    void aJarSignedByAForeignSignerIsRefused() throws Exception {
        Path signed = sign(plainJar("d.jar", "io/x/A.class"), "foreign", "d-foreign.jar");
        JarSignatures.Rejected r = assertThrows(JarSignatures.Rejected.class, () -> JarSignatures.verify(signed, trust));
        assertTrue(r.getMessage().contains("trusted signer"), r.getMessage());
    }

    @Test
    void aSignerWhoseCertificateIsNotForCodeSigningIsRefused() throws Exception {
        Path signed = sign(plainJar("e.jar", "io/x/A.class"), "tlsonly", "e-tls.jar");
        assertThrows(JarSignatures.Rejected.class, () -> JarSignatures.verify(signed, trust));
    }

    @Test
    void anEmptyOrMissingTrustStoreRefusesEverything() throws Exception {
        Path signed = sign(plainJar("f.jar", "io/x/A.class"), "leaf", "f-signed.jar");
        KeyStore empty = KeyStore.getInstance("PKCS12");
        empty.load(null, null);
        assertThrows(JarSignatures.Rejected.class, () -> JarSignatures.verify(signed, empty));
        assertThrows(JarSignatures.Rejected.class, () -> JarSignatures.verify(signed, null));
    }

    @Test
    void aTrustStoreWithOnlyAKeyEntryHasNoAnchor() throws Exception {
        Path signed = sign(plainJar("g.jar", "io/x/A.class"), "leaf", "g-signed.jar");
        // The signer's own keystore: key entries, no trusted-certificate entries.
        assertThrows(JarSignatures.Rejected.class, () -> JarSignatures.verify(signed, load()));
    }

    @Test
    void aSymlinkIsRefused() throws Exception {
        Path signed = sign(plainJar("h.jar", "io/x/A.class"), "leaf", "h-signed.jar");
        Path link = dir.resolve("h-link.jar");
        Files.createSymbolicLink(link, signed);
        assertThrows(JarSignatures.Rejected.class, () -> JarSignatures.verify(link, trust));
    }

    @Test
    void garbageIsRefused() throws Exception {
        Path junk = dir.resolve("junk.jar");
        Files.write(junk, "this is not a zip".getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class, () -> JarSignatures.verify(junk, trust));
    }

    @Test
    void signatureFilesAreRecognised() {
        assertTrue(JarSignatures.isSignatureRelated("META-INF/MANIFEST.MF"));
        assertTrue(JarSignatures.isSignatureRelated("META-INF/SIGNER.SF"));
        assertTrue(JarSignatures.isSignatureRelated("META-INF/signer.ec"));
        assertTrue(JarSignatures.isSignatureRelated("META-INF/SIG-X"));
        assertFalse(JarSignatures.isSignatureRelated("META-INF/maven/a/pom.xml"));
        assertFalse(JarSignatures.isSignatureRelated("io/META-INF/X.SF"));
        assertFalse(JarSignatures.isSignatureRelated("io/x/A.class"));
    }

    @Test
    void theTrustStoreLoaderReadsPkcs12() throws Exception {
        Path p = dir.resolve("trust.p12");
        try (OutputStream os = Files.newOutputStream(p)) { trust.store(os, PW); }
        KeyStore ks = JarSignatures.loadTrustStore(p, PW);
        assertTrue(ks.isCertificateEntry("ca"));
        assertTrue(((X509Certificate) ks.getCertificate("ca")).getBasicConstraints() >= 0);
    }
}
