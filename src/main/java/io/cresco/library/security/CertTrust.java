package io.cresco.library.security;

import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Trust verification for the distributed regional-CA model: a leaf is trusted iff it chains to one of
 * the region-CA certificates the verifier holds (its own region CA plus the region CAs distributed in
 * the trust bundle for the domains it federates with — see
 * {@code docs/distributed-identity-trust-design.md}). Trust anchors are <em>region CA certs</em>, not
 * individual leaf certs, which is what collapses trust material from O(agents) to O(regions).
 *
 * <p>Revocation checking is disabled here on purpose: the design revokes by short leaf lifetimes +
 * re-enrollment rather than mesh-wide CRL/OCSP, so a validator never needs to reach the network to make
 * a decision (it works under partition). Pure JDK PKIX — no provider dependency.
 */
public final class CertTrust {

    private CertTrust() {}

    /**
     * Validate that {@code chain} (leaf first, any intermediates following) terminates in one of
     * {@code trustedCAs}. Returns {@code true} iff a valid, in-date path exists to a trusted region CA.
     */
    public static boolean verifyChain(List<X509Certificate> chain, Collection<X509Certificate> trustedCAs) {
        if (chain == null || chain.isEmpty() || trustedCAs == null || trustedCAs.isEmpty()) {
            return false;
        }
        try {
            Set<TrustAnchor> anchors = new HashSet<>();
            for (X509Certificate ca : trustedCAs) {
                if (ca != null) {
                    anchors.add(new TrustAnchor(ca, null));
                }
            }
            if (anchors.isEmpty()) {
                return false;
            }

            // The CertPath must not itself include a trust anchor; drop any chain elements that are
            // already anchors so a leaf-signed-directly-by-a-trusted-CA case validates cleanly.
            List<X509Certificate> path = new ArrayList<>();
            for (X509Certificate c : chain) {
                if (c == null) continue;
                boolean isAnchor = false;
                for (X509Certificate ca : trustedCAs) {
                    if (c.equals(ca)) { isAnchor = true; break; }
                }
                if (!isAnchor) {
                    path.add(c);
                }
            }
            if (path.isEmpty()) {
                // The presented chain was nothing but trusted anchors — the leaf itself is a trusted CA.
                return true;
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            CertPath certPath = cf.generateCertPath(path);
            PKIXParameters params = new PKIXParameters(anchors);
            params.setRevocationEnabled(false);
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            validator.validate(certPath, params);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Convenience: verify a single leaf directly against a set of trusted region CA certs. */
    public static boolean verifyLeaf(X509Certificate leaf, Collection<X509Certificate> trustedCAs) {
        if (leaf == null) {
            return false;
        }
        List<X509Certificate> chain = new ArrayList<>();
        chain.add(leaf);
        return verifyChain(chain, trustedCAs);
    }

    /**
     * Verify a peer's leaf chains to a trusted region CA <em>and</em> return the identity it asserts,
     * or {@code null} if the chain is untrusted or carries no Cresco identity. This is the single call
     * an agent makes to "identify another agent": trust + identity in one step.
     */
    public static CrescoIdentity verifiedIdentity(List<X509Certificate> chain, Collection<X509Certificate> trustedCAs) {
        if (!verifyChain(chain, trustedCAs)) {
            return null;
        }
        return CrescoIdentity.fromCertificate(chain.get(0));
    }
}
