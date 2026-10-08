package io.cresco.library.plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which config parameters {@link Config} treats as secrets, so that it takes them from an
 * owner-only {@code <param>_file} or the environment ahead of {@code -D} (GaiaKeep OUT-03b).
 * The rule is the controller's {@code io.cresco.agent.core.SecretSources#isSecretParam}: a listed
 * agent secret, or a name matching the controller's {@code ConfigRedaction.SECRET_KEY} (secret,
 * password, passphrase, token, a trailing _key, or pin as a word), but not a name ending in
 * {@code _file} and not one of {@link #NOT_SOURCES}.
 */
public final class SecretParams {

    /** The controller's {@code SecretSources.AGENT_SECRET_PARAMS} (keystorepwd and truststorepwd do not match the pattern). */
    public static final List<String> AGENT_SECRET_PARAMS = Collections.unmodifiableList(Arrays.asList(
            "keystorepwd", "truststorepwd", "db_password", "broker_security_secret",
            "discovery_secret_agent", "discovery_secret_region", "discovery_secret_global"));

    /** The controller's {@code ConfigRedaction.SECRET_KEY}, verbatim. */
    public static final Pattern SECRET_KEY = Pattern.compile(
            "(?i:secret|password|passphrase|token|_key$)"
            + "|(?i:(?:^|[_.-])pin(?:[_.-]|$))"
            + "|(?:^|[_.-])(?:pin|Pin)(?=[A-Z0-9])"
            + "|[a-z0-9](?:Pin|PIN)(?![a-z])");

    /**
     * Parameter families whose {@code <name>_file} is read by their owner with its own rules, so the
     * library must not read it too (or the owner would see the secret twice and refuse to start):
     * db_key (the controller's Derby key file, OUT-03a), and gfs's key families gfs_secret,
     * core_master_key, tape_media_key and gfs_pkcs11_pin (KeySources: key-record files plus
     * {@code _env} indirection; plain values only in dev mode). These and every parameter of the
     * family ({@code gfs_secret_env}, {@code gfs_secret_previous_files}, ...) keep the plain lookup.
     */
    public static final Set<String> NOT_SOURCES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "db_key", "gfs_secret", "core_master_key", "tape_media_key", "gfs_pkcs11_pin")));

    static final String FILE_SUFFIX = "_file";

    private SecretParams() {}

    /** Is {@code param} a secret that Config resolves from a file or the environment first. */
    public static boolean isSecretParam(String param) {
        if (param == null || param.isEmpty() || param.toLowerCase(Locale.ROOT).endsWith(FILE_SUFFIX)) return false;
        for (String family : NOT_SOURCES) {
            if (param.equals(family) || param.startsWith(family + "_")) return false;
        }
        return AGENT_SECRET_PARAMS.contains(param) || SECRET_KEY.matcher(param).find();
    }
}
