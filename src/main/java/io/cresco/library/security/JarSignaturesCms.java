package io.cresco.library.security;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.util.Store;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Jar signature verification that runs entirely through the JCA providers the node installed -- under
 * {@code security_profile=production} the FIPS 140-3 modules in approved-only mode -- instead of the JDK's internal jar
 * verifier (sun.security.pkcs and its own manifest digester, outside any FIPS module boundary).
 *
 * <p>What it checks is what {@link JarSignatures#verify} checks, by hand:
 * <ul>
 *   <li>every signature block {@code META-INF/<NAME>.RSA|.EC|.DSA} is a CMS SignedData over {@code META-INF/<NAME>.SF},
 *       verified with Bouncy Castle FIPS's CMS API over the default (FIPS) providers; its signer's certificate path must
 *       validate (PKIX, in date) to the trust store and permit code signing; a trusted RFC 3161 timestamp in its unsigned
 *       attributes (the TSA chains to the same trust store, carries the time-stamping usage, and its token's imprint is the
 *       digest of this signature) moves the date it is judged at to the stamped time;</li>
 *   <li>a trusted {@code .SF} covers the manifest -- its {@code <ALG>-Digest-Manifest} is the digest of the whole manifest,
 *       or per entry its {@code Name:} section's {@code <ALG>-Digest} is the digest of that manifest section's bytes;</li>
 *   <li>every entry (but directories and the signature files) has a manifest section that a trusted {@code .SF} covers,
 *       and its content matches that section's {@code <ALG>-Digest}.</li>
 * </ul>
 * Only SHA-256, SHA-384 and SHA-512 digests count (a jar digested with SHA-1 or MD5 only is unsigned here). Bounded by the
 * same entry cap; the zip itself was checked by the caller (one central directory, matching local headers).
 */
final class JarSignaturesCms {

    private JarSignaturesCms() {}

    /** Manifest digest attribute prefixes counted (approved), and their JCA names. */
    private static final String[][] DIGESTS = {{"SHA-512", "SHA-512"}, {"SHA-384", "SHA-384"}, {"SHA-256", "SHA-256"}};
    /** Imprint algorithms of a timestamp token, by OID. */
    private static final Map<String, String> IMPRINT = Map.of(
            "2.16.840.1.101.3.4.2.1", "SHA-256", "2.16.840.1.101.3.4.2.2", "SHA-384", "2.16.840.1.101.3.4.2.3", "SHA-512");
    /** The signer's digest algorithm (CMS): approved ones only. */
    private static final Set<String> SIGNER_DIGESTS = IMPRINT.keySet();

    /** One manifest (or .SF) section: its exact bytes (blank line included) and attributes. */
    static final class Section {
        final byte[] raw;
        final Map<String, String> attrs;
        Section(byte[] raw, Map<String, String> attrs) { this.raw = raw; this.attrs = attrs; }
    }

    /** A parsed manifest: its main section and its named sections, each with its exact bytes. */
    static final class Parsed {
        Section main;
        final Map<String, Section> named = new LinkedHashMap<>();
    }

    /**
     * Verify {@code jar} (its central directory {@code names}, read by the caller) against {@code anchors}.
     *
     * @return how many entries are signed (0: the caller refuses the jar)
     */
    static int verify(Path jar, List<X509Certificate> anchors) throws IOException, GeneralSecurityException {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Map<String, ZipEntry> entries = new HashMap<>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (entries.put(e.getName(), e) != null) throw new JarSignatures.Rejected("duplicate entry " + e.getName());
            }
            ZipEntry mfe = entries.get("META-INF/MANIFEST.MF");
            if (mfe == null) throw new JarSignatures.Rejected(jar.getFileName() + " has no manifest, so it is not signed");
            byte[] manifest = read(zf, mfe);
            Parsed mf = parse(manifest);

            // the signers: each block over its .SF, verified through the providers, chained to the trust store
            boolean wholeManifest = false;
            Set<String> covered = new HashSet<>();
            for (String name : entries.keySet()) {
                String up = name.toUpperCase(Locale.ROOT);
                if (!up.startsWith("META-INF/") || up.indexOf('/', 9) >= 0) continue;
                if (!(up.endsWith(".RSA") || up.endsWith(".EC") || up.endsWith(".DSA"))) continue;
                String base = name.substring(0, name.lastIndexOf('.'));
                ZipEntry sfe = entries.get(base + ".SF");
                if (sfe == null) continue;
                byte[] sf = read(zf, sfe);
                if (!trustedSigner(read(zf, entries.get(name)), sf, anchors)) continue;
                Parsed sig = parse(sf);
                if (digestMatches(sig.main.attrs, "-Digest-Manifest", manifest)) {
                    wholeManifest = true;
                } else if (digestMatches(sig.main.attrs, "-Digest-Manifest-Main-Attributes", mf.main.raw)) {
                    // the manifest changed since signing (sections added): the main attributes (Bundle-*, Import-Package)
                    // must still be the signed ones, and only the signed sections count
                    for (Map.Entry<String, Section> s : sig.named.entrySet()) {
                        Section ms = mf.named.get(s.getKey());
                        if (ms != null && digestMatches(s.getValue().attrs, "-Digest", ms.raw)) covered.add(s.getKey());
                    }
                }
            }

            // every entry: covered by a trusted signer, and its content as its manifest section says
            int signed = 0;
            for (Map.Entry<String, ZipEntry> e : entries.entrySet()) {
                String name = e.getKey();
                if (e.getValue().isDirectory() || JarSignatures.isSignatureRelated(name)) continue;
                Section ms = mf.named.get(name);
                if (ms == null || !(wholeManifest || covered.contains(name))) {
                    throw new JarSignatures.Rejected("entry " + name + " is not signed by a trusted signer");
                }
                if (!digestMatches(ms.attrs, "-Digest", e.getValue(), zf)) {
                    throw new JarSignatures.Rejected("entry " + name + " does not match its signed digest");
                }
                signed++;
            }
            return signed;
        }
    }

    /** Does the CMS block verify over {@code sf}, by a signer that chains to {@code anchors} and may sign code? */
    static boolean trustedSigner(byte[] block, byte[] sf, List<X509Certificate> anchors) {
        try {
            CMSSignedData sd = new CMSSignedData(new CMSProcessableByteArray(sf), block);
            @SuppressWarnings("unchecked")
            Store<X509CertificateHolder> store = sd.getCertificates();
            List<X509Certificate> all = new ArrayList<>();
            JcaX509CertificateConverter conv = new JcaX509CertificateConverter();
            for (X509CertificateHolder h : store.getMatches(null)) all.add(conv.getCertificate(h));
            for (SignerInformation si : sd.getSignerInfos().getSigners()) {
                if (!SIGNER_DIGESTS.contains(si.getDigestAlgOID())) continue;
                @SuppressWarnings("unchecked")
                Collection<X509CertificateHolder> match = store.getMatches(si.getSID());
                if (match.isEmpty()) continue;
                X509CertificateHolder holder = match.iterator().next();
                if (!si.verify(new JcaSimpleSignerInfoVerifierBuilder().build(holder))) continue;
                List<X509Certificate> chain = chainOf(conv.getCertificate(holder), all);
                if (!JarSignatures.permitsCodeSigning(chain.get(0))) continue;
                Date at = trustedTimestamp(si, anchors);
                if (CertTrust.verifyChain(chain, anchors, at == null ? new Date() : at)) return true;
            }
        } catch (Exception | LinkageError e) {
            return false;
        }
        return false;
    }

    /**
     * The time a signer's RFC 3161 timestamp vouches for, or null: the token verifies under its TSA certificate, its imprint
     * is the digest of this signature, the TSA carries the time-stamping usage and chains (valid then) to {@code anchors},
     * and the time is not in the future.
     */
    static Date trustedTimestamp(SignerInformation si, List<X509Certificate> anchors) {
        try {
            AttributeTable unsigned = si.getUnsignedAttributes();
            Attribute a = unsigned == null ? null : unsigned.get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
            if (a == null || a.getAttrValues().size() == 0) return null;
            TimeStampToken tst = new TimeStampToken(new CMSSignedData(a.getAttrValues().getObjectAt(0).toASN1Primitive().getEncoded()));
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> match = tst.getCertificates().getMatches(tst.getSID());
            if (match.isEmpty()) return null;
            X509CertificateHolder tsa = match.iterator().next();
            tst.validate(new JcaSimpleSignerInfoVerifierBuilder().build(tsa));
            ASN1ObjectIdentifier alg = tst.getTimeStampInfo().getMessageImprintAlgOID();
            String name = IMPRINT.get(alg.getId());
            if (name == null) return null;
            if (!MessageDigest.isEqual(MessageDigest.getInstance(name).digest(si.getSignature()), tst.getTimeStampInfo().getMessageImprintDigest())) {
                return null;
            }
            Date at = tst.getTimeStampInfo().getGenTime();
            if (at == null || at.getTime() > System.currentTimeMillis() + JarSignatures.MAX_TIMESTAMP_SKEW_MS) return null;
            JcaX509CertificateConverter conv = new JcaX509CertificateConverter();
            List<X509Certificate> all = new ArrayList<>();
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> certs = tst.getCertificates().getMatches(null);
            for (X509CertificateHolder h : certs) all.add(conv.getCertificate(h));
            List<X509Certificate> chain = chainOf(conv.getCertificate(tsa), all);
            List<String> eku = chain.get(0).getExtendedKeyUsage();
            if (eku == null || !eku.contains(JarSignatures.EKU_TIME_STAMPING)) return null;
            return CertTrust.verifyChain(chain, anchors, at) ? at : null;
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** {@code leaf}, then its issuers among {@code pool} (by name, at most 10 deep). */
    static List<X509Certificate> chainOf(X509Certificate leaf, List<X509Certificate> pool) {
        List<X509Certificate> chain = new ArrayList<>();
        chain.add(leaf);
        X509Certificate cur = leaf;
        for (int i = 0; i < 10 && !cur.getIssuerX500Principal().equals(cur.getSubjectX500Principal()); i++) {
            X509Certificate next = null;
            for (X509Certificate c : pool) {
                if (!chain.contains(c) && c.getSubjectX500Principal().equals(cur.getIssuerX500Principal())) { next = c; break; }
            }
            if (next == null) break;
            chain.add(next);
            cur = next;
        }
        return chain;
    }

    /** Does an approved {@code <ALG><suffix>} attribute of {@code attrs} hold the digest of {@code data}? (all present must) */
    private static boolean digestMatches(Map<String, String> attrs, String suffix, byte[] data) throws GeneralSecurityException {
        boolean any = false;
        for (String[] d : DIGESTS) {
            String v = attrs.get((d[0] + suffix).toLowerCase(Locale.ROOT));
            if (v == null) continue;
            byte[] want;
            try { want = Base64.getDecoder().decode(v.trim()); } catch (IllegalArgumentException bad) { return false; }
            if (!MessageDigest.isEqual(MessageDigest.getInstance(d[1]).digest(data), want)) return false;
            any = true;
        }
        return any;
    }

    /** As above over an entry's content, streamed (an entry is read once per approved digest it names). */
    private static boolean digestMatches(Map<String, String> attrs, String suffix, ZipEntry e, ZipFile zf)
            throws GeneralSecurityException, IOException {
        boolean any = false;
        for (String[] d : DIGESTS) {
            String v = attrs.get((d[0] + suffix).toLowerCase(Locale.ROOT));
            if (v == null) continue;
            byte[] want;
            try { want = Base64.getDecoder().decode(v.trim()); } catch (IllegalArgumentException bad) { return false; }
            MessageDigest md = MessageDigest.getInstance(d[1]);
            byte[] buf = new byte[65536];
            try (InputStream in = zf.getInputStream(e)) {
                for (int n; (n = in.read(buf)) != -1; ) md.update(buf, 0, n);
            }
            if (!MessageDigest.isEqual(md.digest(), want)) return false;
            any = true;
        }
        return any;
    }

    private static byte[] read(ZipFile zf, ZipEntry e) throws IOException, GeneralSecurityException {
        if (e == null) throw new JarSignatures.Rejected("a signature file is missing");
        if (e.getSize() > 64L * 1024 * 1024) throw new JarSignatures.Rejected(e.getName() + " is too large");
        try (InputStream in = zf.getInputStream(e)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    /**
     * Parse a manifest or signature file into its sections, keeping each section's exact bytes up to and including the blank
     * line that ends it (what the digests of {@code .SF} sections cover). Attribute names are lower-cased; continuation lines
     * (starting with one space) are joined.
     */
    static Parsed parse(byte[] b) throws JarSignatures.Rejected {
        Parsed p = new Parsed();
        int i = 0, n = b.length;
        boolean first = true;
        while (i < n) {
            int start = i;
            Map<String, String> attrs = new LinkedHashMap<>();
            String lastKey = null;
            boolean ended = false;
            while (i < n) {
                int eol = i;
                while (eol < n && b[eol] != '\n' && b[eol] != '\r') eol++;
                String line = new String(b, i, eol - i, StandardCharsets.UTF_8);
                int next = eol;
                if (next < n && b[next] == '\r') next++;
                if (next < n && b[next] == '\n') next++;
                i = next;
                if (line.isEmpty()) { ended = true; break; }
                if (line.charAt(0) == ' ') {
                    if (lastKey == null) throw new JarSignatures.Rejected("a manifest continuation line without an attribute");
                    attrs.put(lastKey, attrs.get(lastKey) + line.substring(1));
                    continue;
                }
                int colon = line.indexOf(": ");
                if (colon <= 0) throw new JarSignatures.Rejected("a malformed manifest line");
                lastKey = line.substring(0, colon).toLowerCase(Locale.ROOT);
                if (attrs.containsKey(lastKey)) throw new JarSignatures.Rejected("a repeated manifest attribute " + lastKey);
                attrs.put(lastKey, line.substring(colon + 2));
            }
            byte[] raw = java.util.Arrays.copyOfRange(b, start, i);
            Section s = new Section(raw, attrs);
            if (first) {
                p.main = s;
                first = false;
            } else if (!attrs.isEmpty()) {
                String name = attrs.get("name");
                if (name == null) throw new JarSignatures.Rejected("a manifest section without a Name");
                if (p.named.put(name, s) != null) throw new JarSignatures.Rejected("a repeated manifest section " + name);
            }
            if (!ended) break;
        }
        if (p.main == null) p.main = new Section(new byte[0], new LinkedHashMap<>());
        return p;
    }
}
