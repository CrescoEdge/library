package io.cresco.library.crypto;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The fabric-wide crypto policy: the algorithm types a Cresco fabric and every plugin on it (GaiaKeep storage
 * included) may choose when they make something new (a certificate, a derived key, a storage domain's content
 * address, a wrapped key). One policy per fabric, set at the global controller and enforced everywhere
 * (owner 2026-10-16: "enforced universal settings for allowed types"; gfs OUT-95).
 *
 * <p><b>Config (global controller only).</b> {@code crypto_policy} names a baseline level; {@code crypto_allow_<family>}
 * (hash, sig, kdf, cipher, wrap) replaces one family's list. Levels:
 * <ul>
 *   <li>{@code cnsa1} (default): SHA-384/SHA-512, AES-256-GCM, SHA-384/512 signatures and KDFs (CNSA 1.0
 *       strength, all FIPS 140-3 approved). SHA-256 is not allowed for anything new.</li>
 *   <li>{@code fips140-3}: every FIPS 140-3 approved algorithm Cresco and GaiaKeep can use, SHA-256 and AES-128 included.</li>
 * </ul>
 * An allow-list may only name FIPS 140-3 approved algorithms ({@link #approved}); anything else (MD5, SHA-1, ...)
 * refuses the configuration, so no setting takes a fabric below approved crypto. The level gives a reviewed
 * baseline; an allow-list records a deliberate exception (e.g. {@code crypto_allow_hash=SHA-384,SHA-512,SHA-256} so a
 * storage domain can match external SHA-256 content addresses) without dropping every other family to a weaker level.
 *
 * <p><b>Distribution.</b> The global controller builds the policy from its own config; every region and agent
 * takes the global's policy (registration replies and parent pongs carry it) and ignores local crypto keys, so a
 * node never runs a weaker local override. The node's current policy is {@link #current()}; plugins read it
 * through {@code PluginBuilder.getCryptoPolicy()}.
 *
 * <p><b>Failure.</b> A node that has not received the policy yet holds none: {@link #requireForNew} refuses every
 * algorithm choice for a new object (fail closed). Only bootstrap crypto that must run before a node can reach the
 * global (its own TLS identity, operator-secret KDFs) uses {@link #effective()}, which falls back to {@link #DEFAULT}.
 *
 * <p>The policy governs choices for <em>new</em> objects and formats. Existing data, stored formats and fixed wire
 * protocols keep the algorithm they were written with (reading or verifying them is never refused here).
 */
public final class CryptoPolicy {

    /** The algorithm families a policy lists. {@link #key} is the canonical-form key and the config suffix. */
    public enum Family {
        HASH("hash"), SIGNATURE("sig"), KDF("kdf"), CIPHER("cipher"), WRAP("wrap");

        public final String key;

        Family(String key) {
            this.key = key;
        }

        public String configKey() {
            return CONFIG_ALLOW_PREFIX + key;
        }
    }

    /** A refused algorithm choice; the message names the policy (level and id), the algorithm and what was asked. */
    public static final class Refused extends RuntimeException {
        public Refused(String message) {
            super(message);
        }
    }

    public static final String LEVEL_CNSA1 = "cnsa1";
    public static final String LEVEL_FIPS140_3 = "fips140-3";
    /** Config key of the baseline level (global controller only). */
    public static final String CONFIG_LEVEL = "crypto_policy";
    /** Config key prefix of the per-family allow-lists (global controller only). */
    public static final String CONFIG_ALLOW_PREFIX = "crypto_allow_";
    /** Version tag of the canonical text form; a node refuses a form it does not know. */
    public static final String FORMAT = "cresco-crypto-policy/1";

    // The FIPS 140-3 approved algorithms Cresco and GaiaKeep can use, per family, in preference order (the first
    // allowed one is what a selector such as signatureFor picks). Names as JCA spells them where JCA has one.
    private static final Map<Family, List<String>> APPROVED = new EnumMap<>(Family.class);
    private static final Map<String, Map<Family, List<String>>> LEVELS = new LinkedHashMap<>();

    static {
        APPROVED.put(Family.HASH, List.of("SHA-384", "SHA-512", "SHA3-384", "SHA3-512", "SHA-256", "SHA3-256"));
        APPROVED.put(Family.SIGNATURE, List.of("SHA384withRSA", "SHA512withRSA", "SHA384withECDSA", "SHA512withECDSA",
                "SHA256withRSA", "SHA256withECDSA"));
        APPROVED.put(Family.KDF, List.of("PBKDF2WithHmacSHA384", "PBKDF2WithHmacSHA512", "HKDF-SHA384", "HKDF-SHA512",
                "PBKDF2WithHmacSHA256", "HKDF-SHA256"));
        APPROVED.put(Family.CIPHER, List.of("AES-256-GCM", "AES-192-GCM", "AES-128-GCM"));
        APPROVED.put(Family.WRAP, List.of("AES-KWP", "AES-KW", "AES-GCM"));

        Map<Family, List<String>> cnsa1 = new EnumMap<>(Family.class);
        cnsa1.put(Family.HASH, List.of("SHA-384", "SHA-512"));
        cnsa1.put(Family.SIGNATURE, List.of("SHA384withRSA", "SHA512withRSA", "SHA384withECDSA", "SHA512withECDSA"));
        cnsa1.put(Family.KDF, List.of("PBKDF2WithHmacSHA384", "PBKDF2WithHmacSHA512", "HKDF-SHA384", "HKDF-SHA512"));
        cnsa1.put(Family.CIPHER, List.of("AES-256-GCM"));
        cnsa1.put(Family.WRAP, List.of("AES-KWP", "AES-KW", "AES-GCM"));
        LEVELS.put(LEVEL_CNSA1, cnsa1);
        LEVELS.put(LEVEL_FIPS140_3, APPROVED);
    }

    /** The default policy: level {@code cnsa1}, no overrides. What a node uses for bootstrap crypto before it holds the fabric's. */
    public static final CryptoPolicy DEFAULT = level(LEVEL_CNSA1);

    private final String level;
    private final Map<Family, List<String>> allowed;
    private final String canonical;
    private final String id;

    private CryptoPolicy(String level, Map<Family, List<String>> allowed) {
        this.level = level;
        Map<Family, List<String>> m = new EnumMap<>(Family.class);
        StringBuilder sb = new StringBuilder(FORMAT).append(";level=").append(level);
        for (Family f : Family.values()) {
            List<String> set = Collections.unmodifiableList(new ArrayList<>(allowed.get(f)));
            m.put(f, set);
            sb.append(';').append(f.key).append('=').append(String.join(",", set));
        }
        this.allowed = Collections.unmodifiableMap(m);
        this.canonical = sb.toString();
        String h;
        try {
            h = CrescoCrypto.digestHex(canonical.getBytes(StandardCharsets.UTF_8), "SHA-384").substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-384 unavailable", e);
        }
        this.id = h;
    }

    // ------------------------------------------------------------------ construction

    /** The named level with no overrides. */
    public static CryptoPolicy level(String level) {
        return of(level, Collections.emptyMap());
    }

    /**
     * A level with per-family allow-lists replacing its lists. Every name must be an approved algorithm of that
     * family (aliases such as {@code SHA384} are accepted and canonicalised) and no family may end up empty.
     *
     * @throws IllegalArgumentException naming the bad level, family or algorithm
     */
    public static CryptoPolicy of(String level, Map<Family, String> allowOverrides) {
        String lv = level == null ? LEVEL_CNSA1 : level.trim().toLowerCase(Locale.ROOT);
        Map<Family, List<String>> base = LEVELS.get(lv);
        if (base == null) {
            throw new IllegalArgumentException(CONFIG_LEVEL + "=" + level + " is not a known level (one of " + LEVELS.keySet() + ")");
        }
        Map<Family, List<String>> m = new EnumMap<>(Family.class);
        for (Family f : Family.values()) {
            String spec = allowOverrides.get(f);
            m.put(f, spec == null || spec.isBlank() ? base.get(f) : parseList(f, spec));
        }
        return new CryptoPolicy(lv, m);
    }

    /**
     * The policy a global controller's config names: {@code crypto_policy} (default {@code cnsa1}) and any
     * {@code crypto_allow_<family>} lists. {@code get} returns null for an absent key.
     */
    public static CryptoPolicy fromConfig(Function<String, String> get) {
        Map<Family, String> over = new EnumMap<>(Family.class);
        for (Family f : Family.values()) {
            String v = get.apply(f.configKey());
            if (v != null && !v.isBlank()) {
                over.put(f, v);
            }
        }
        return of(get.apply(CONFIG_LEVEL), over);
    }

    /** True when {@code get} sets any crypto policy key (a non-global node ignores them; it warns that it does). */
    public static boolean configSetsPolicy(Function<String, String> get) {
        if (get.apply(CONFIG_LEVEL) != null) {
            return true;
        }
        for (Family f : Family.values()) {
            if (get.apply(f.configKey()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses the canonical form ({@link #canonical()}) a parent sent. Strict: the format tag, a known level and
     * every family must be present, and every name approved.
     */
    public static CryptoPolicy parse(String canonical) {
        if (canonical == null || !canonical.startsWith(FORMAT + ";")) {
            throw new IllegalArgumentException("not a " + FORMAT + " policy");
        }
        String level = null;
        Map<Family, String> lists = new EnumMap<>(Family.class);
        for (String part : canonical.substring(FORMAT.length() + 1).split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("malformed policy field '" + part + "'");
            }
            String k = part.substring(0, eq);
            String v = part.substring(eq + 1);
            if (k.equals("level")) {
                level = v;
                continue;
            }
            Family f = familyOf(k);
            if (f == null || lists.put(f, v) != null) {
                throw new IllegalArgumentException("unknown or repeated policy field '" + k + "'");
            }
        }
        if (level == null || !LEVELS.containsKey(level) || lists.size() != Family.values().length) {
            throw new IllegalArgumentException("incomplete policy (level and every family are required)");
        }
        return of(level, lists);
    }

    private static Family familyOf(String key) {
        for (Family f : Family.values()) {
            if (f.key.equals(key)) {
                return f;
            }
        }
        return null;
    }

    private static List<String> parseList(Family f, String spec) {
        List<String> picked = new ArrayList<>();
        for (String raw : spec.split(",")) {
            if (raw.isBlank()) {
                continue;
            }
            String name = approvedName(f, raw);
            if (name == null) {
                throw new IllegalArgumentException(f.configKey() + ": " + raw.trim() + " is not a FIPS 140-3 approved "
                        + f.key + " algorithm Cresco offers (one of " + APPROVED.get(f) + ")");
            }
            if (!picked.contains(name)) {
                picked.add(name);
            }
        }
        if (picked.isEmpty()) {
            throw new IllegalArgumentException(f.configKey() + " allows nothing");
        }
        // canonical order is the approved (preference) order, whatever order the config listed
        List<String> ordered = new ArrayList<>();
        for (String a : APPROVED.get(f)) {
            if (picked.contains(a)) {
                ordered.add(a);
            }
        }
        return ordered;
    }

    /** The approved spelling of {@code alg} in family {@code f} (case, '-', '_' and '/' ignored), or null. */
    public static String approvedName(Family f, String alg) {
        if (alg == null) {
            return null;
        }
        String k = squash(alg);
        for (String a : APPROVED.get(f)) {
            if (squash(a).equals(k)) {
                return a;
            }
        }
        return null;
    }

    private static String squash(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    /** The FIPS 140-3 approved algorithms of a family that a policy may list, in preference order. */
    public static List<String> approved(Family f) {
        return APPROVED.get(f);
    }

    // ------------------------------------------------------------------ queries

    public String level() { return level; }

    /** The allowed algorithms of a family, in preference order. */
    public List<String> allowed(Family f) { return allowed.get(f); }

    /** The canonical text form: what the global sends down and what {@link #id()} hashes. */
    public String canonical() { return canonical; }

    /** First 16 hex digits of SHA-384 over {@link #canonical()}: the policy's short name in logs, errors and pings. */
    public String id() { return id; }

    /** True when this policy is exactly its level's baseline (no allow-list overrides). */
    public boolean isBaseline() { return level(level).canonical.equals(canonical); }

    /** Short description for errors and logs: {@code cnsa1 (id 0123456789abcdef)}, {@code +overrides} when lists were replaced. */
    public String describe() {
        return level + (isBaseline() ? "" : "+overrides") + " (id " + id + ")";
    }

    public boolean allows(Family f, String alg) {
        String name = approvedName(f, alg);
        return name != null && allowed.get(f).contains(name);
    }

    /**
     * Refuses {@code alg} unless this policy allows it for family {@code f}.
     *
     * @param what the new object the algorithm is being chosen for, e.g. "the block hash of new domain d1"
     */
    public void require(Family f, String alg, String what) {
        if (!allows(f, alg)) {
            throw new Refused("crypto policy " + describe() + " does not allow " + f.key + " " + alg + " for " + what
                    + "; allowed " + f.key + ": " + String.join(", ", allowed.get(f))
                    + " (set fabric-wide at the global controller: " + CONFIG_LEVEL + ", " + f.configKey() + ")");
        }
    }

    /** The preferred allowed signature algorithm for a key of type {@code keyAlgorithm} (RSA or EC). */
    public String signatureFor(String keyAlgorithm) {
        String suffix = keyAlgorithm != null && keyAlgorithm.toUpperCase(Locale.ROOT).startsWith("EC") ? "withECDSA" : "withRSA";
        for (String a : allowed.get(Family.SIGNATURE)) {
            if (a.endsWith(suffix)) {
                return a;
            }
        }
        throw new Refused("crypto policy " + describe() + " allows no " + suffix.substring(4) + " signature algorithm; allowed sig: "
                + String.join(", ", allowed.get(Family.SIGNATURE)));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CryptoPolicy && ((CryptoPolicy) o).canonical.equals(canonical);
    }

    @Override
    public int hashCode() { return canonical.hashCode(); }

    @Override
    public String toString() { return canonical; }

    // ------------------------------------------------------------------ the node's current policy

    private static final class Held {
        final CryptoPolicy policy;
        final String source;

        Held(CryptoPolicy policy, String source) {
            this.policy = policy;
            this.source = source;
        }
    }

    private static volatile Held held;

    /** The fabric policy this node holds, or null before it has one (then every {@link #requireForNew} refuses). */
    public static CryptoPolicy current() {
        Held h = held;
        return h == null ? null : h.policy;
    }

    /** Where the current policy came from ("local config of the global controller r/a", "parent r/a"), or null. */
    public static String currentSource() {
        Held h = held;
        return h == null ? null : h.source;
    }

    /**
     * Installs the node's fabric policy. Only the controller calls this: from its own config when it is the
     * global (or standalone) controller, otherwise from its parent's reply.
     *
     * @return true when the policy changed
     */
    public static synchronized boolean install(CryptoPolicy policy, String source) {
        if (policy == null) {
            throw new IllegalArgumentException("policy");
        }
        Held h = held;
        if (h != null && h.policy.equals(policy)) {
            return false;
        }
        held = new Held(policy, source);
        return true;
    }

    /** Drops the held policy (tests; a node that leaves its fabric). Every new-object choice then refuses. */
    public static synchronized void uninstall() {
        held = null;
    }

    /**
     * The policy for bootstrap crypto that must work before the node can reach the global (its own TLS identity,
     * operator-secret key derivation): the fabric policy when held, else {@link #DEFAULT}.
     */
    public static CryptoPolicy effective() {
        CryptoPolicy p = current();
        return p == null ? DEFAULT : p;
    }

    /**
     * Refuses {@code alg} for a new object unless the fabric policy this node holds allows it; with no policy
     * held yet it refuses everything (fail closed).
     */
    public static void requireForNew(Family f, String alg, String what) {
        Held h = held;
        if (h == null) {
            throw new Refused("no fabric crypto policy has reached this node yet (it comes from the global controller); "
                    + "refusing to choose " + f.key + " " + alg + " for " + what + " until it does");
        }
        h.policy.require(f, alg, what);
    }
}
