package io.cresco.library.crypto;

import io.cresco.library.crypto.CryptoPolicy.Family;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CryptoPolicyTest {

    @AfterEach
    void clear() {
        CryptoPolicy.uninstall();
    }

    @Test
    void defaultIsCnsa1AndRefusesSha256ForNewObjects() {
        CryptoPolicy p = CryptoPolicy.DEFAULT;
        assertEquals("cnsa1", p.level());
        assertTrue(p.allows(Family.HASH, "SHA-384"));
        assertTrue(p.allows(Family.HASH, "SHA384"));          // gfs BlockHash spelling
        assertTrue(p.allows(Family.HASH, "sha-512"));
        assertFalse(p.allows(Family.HASH, "SHA-256"));
        assertFalse(p.allows(Family.HASH, "SHA256"));
        assertFalse(p.allows(Family.HASH, "MD5"));
        assertFalse(p.allows(Family.CIPHER, "AES-128-GCM"));
        assertTrue(p.allows(Family.WRAP, "AES-KWP"));
        assertTrue(p.allows(Family.WRAP, "AES-GCM"));
        assertEquals("SHA384withRSA", p.signatureFor("RSA"));
        assertEquals("SHA384withECDSA", p.signatureFor("EC"));
        CryptoPolicy.Refused r = assertThrows(CryptoPolicy.Refused.class,
                () -> p.require(Family.HASH, "SHA256", "the block hash of new domain d1"));
        assertTrue(r.getMessage().contains("cnsa1 (id " + p.id() + ")"), r.getMessage());
        assertTrue(r.getMessage().contains("SHA256") && r.getMessage().contains("SHA-384, SHA-512"), r.getMessage());
        assertTrue(r.getMessage().contains("crypto_allow_hash"), r.getMessage());
    }

    @Test
    void fipsLevelAllowsSha256() {
        CryptoPolicy p = CryptoPolicy.level("FIPS140-3");
        assertTrue(p.allows(Family.HASH, "SHA-256"));
        assertTrue(p.allows(Family.CIPHER, "AES-128-GCM"));
        assertTrue(p.isBaseline());
        assertEquals("SHA384withRSA", p.signatureFor("RSA"));  // preference order, not weakest-first
    }

    @Test
    void allowListReplacesOneFamilyAndCanonicalisesOrder() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("crypto_allow_hash", "sha256, SHA-512 ,SHA-384");
        CryptoPolicy p = CryptoPolicy.fromConfig(cfg::get);
        assertEquals("cnsa1", p.level());
        assertEquals(List.of("SHA-384", "SHA-512", "SHA-256"), p.allowed(Family.HASH));
        assertEquals(CryptoPolicy.DEFAULT.allowed(Family.CIPHER), p.allowed(Family.CIPHER));
        assertFalse(p.isBaseline());
        assertTrue(p.describe().startsWith("cnsa1+overrides (id "));
        assertNotEquals(CryptoPolicy.DEFAULT.id(), p.id());
    }

    @Test
    void configCannotNameUnapprovedAlgorithmsOrUnknownLevels() {
        assertThrows(IllegalArgumentException.class, () -> CryptoPolicy.fromConfig(k -> k.equals("crypto_allow_hash") ? "SHA-1" : null));
        assertThrows(IllegalArgumentException.class, () -> CryptoPolicy.fromConfig(k -> k.equals("crypto_allow_hash") ? "MD5,SHA-384" : null));
        assertThrows(IllegalArgumentException.class, () -> CryptoPolicy.fromConfig(k -> k.equals("crypto_allow_sig") ? "SHA1withRSA" : null));
        assertThrows(IllegalArgumentException.class, () -> CryptoPolicy.fromConfig(k -> k.equals("crypto_allow_cipher") ? " , " : null));
        assertThrows(IllegalArgumentException.class, () -> CryptoPolicy.fromConfig(k -> k.equals("crypto_policy") ? "legacy" : null));
        assertTrue(CryptoPolicy.configSetsPolicy(k -> k.equals("crypto_allow_wrap") ? "AES-KW" : null));
        assertFalse(CryptoPolicy.configSetsPolicy(k -> null));
    }

    @Test
    void canonicalFormRoundTripsAndParseIsStrict() {
        CryptoPolicy p = CryptoPolicy.of("cnsa1", Map.of(Family.HASH, "SHA-384,SHA-256"));
        CryptoPolicy back = CryptoPolicy.parse(p.canonical());
        assertEquals(p, back);
        assertEquals(p.id(), back.id());
        assertTrue(p.canonical().startsWith(CryptoPolicy.FORMAT + ";level=cnsa1;hash=SHA-384,SHA-256;sig="), p.canonical());
        assertThrows(IllegalArgumentException.class, () -> CryptoPolicy.parse("cresco-crypto-policy/9;level=cnsa1"));
        assertThrows(IllegalArgumentException.class, () -> CryptoPolicy.parse(CryptoPolicy.FORMAT + ";level=cnsa1;hash=SHA-384"));
        assertThrows(IllegalArgumentException.class, () -> CryptoPolicy.parse(p.canonical().replace("hash=SHA-384,SHA-256", "hash=MD5")));
        assertThrows(IllegalArgumentException.class, () -> CryptoPolicy.parse(p.canonical() + ";bogus=x"));
        assertThrows(IllegalArgumentException.class, () -> CryptoPolicy.parse(null));
    }

    @Test
    void noHeldPolicyFailsClosedForNewObjectsButBootstrapUsesDefault() {
        assertNull(CryptoPolicy.current());
        CryptoPolicy.Refused r = assertThrows(CryptoPolicy.Refused.class,
                () -> CryptoPolicy.requireForNew(Family.HASH, "SHA-384", "a new domain"));
        assertTrue(r.getMessage().contains("no fabric crypto policy"), r.getMessage());
        assertEquals(CryptoPolicy.DEFAULT, CryptoPolicy.effective());
        assertEquals("SHA384withRSA", CrescoCrypto.signatureAlgorithm("RSA"));

        CryptoPolicy fips = CryptoPolicy.level("fips140-3");
        assertTrue(CryptoPolicy.install(fips, "test"));
        assertFalse(CryptoPolicy.install(CryptoPolicy.parse(fips.canonical()), "test again"));   // same policy: no change
        assertEquals("test", CryptoPolicy.currentSource());
        CryptoPolicy.requireForNew(Family.HASH, "SHA-256", "a new domain");
        assertSame(fips, CryptoPolicy.effective());
    }

    @Test
    void signatureSelectionRefusesWhenTheKeyTypeHasNoAllowedSuite() {
        CryptoPolicy ecOnly = CryptoPolicy.of("cnsa1", Map.of(Family.SIGNATURE, "SHA384withECDSA"));
        assertEquals("SHA384withECDSA", ecOnly.signatureFor("EC"));
        assertThrows(CryptoPolicy.Refused.class, () -> ecOnly.signatureFor("RSA"));
    }
}
