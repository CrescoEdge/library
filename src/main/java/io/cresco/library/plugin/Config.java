package io.cresco.library.plugin;


import io.cresco.library.security.OwnerOnlyFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Cresco configuration handler.
 *
 * <p>A parameter is looked up in the JVM system properties ({@code -D<param>}), then the environment
 * variable {@code CRESCO_<param>} (exact case), then the plugin config map.
 *
 * <p><b>Secret parameters</b> ({@link SecretParams#isSecretParam}: names with secret, password,
 * passphrase, token, pin, a trailing _key, or the agent's keystorepwd/truststorepwd/...) are checked
 * first in two more places, because a {@code -D} value is visible to every local user in
 * {@code ps} and {@code /proc/<pid>/cmdline} (GaiaKeep OUT-03b):
 * <ol>
 *   <li>a file named by {@code <param>_file}, given as {@code -D<param>_file}, the environment
 *       variable {@code CRESCO_<PARAM>_FILE} (upper case; the exact-case {@code CRESCO_<param>_file}
 *       is accepted too) or the plugin config. The file must be a regular file owned by the JVM
 *       user, mode 0600 or 0400, in a directory only that user or root can write
 *       ({@link OwnerOnlyFile}); otherwise the lookup throws an {@link IllegalStateException}
 *       naming the parameter and the file, never the value. Surrounding whitespace is stripped.
 *       The file is read once per Config (per param and path) and cached: a rotated file is picked
 *       up when the plugin or agent restarts;</li>
 *   <li>the environment variable {@code CRESCO_<PARAM>} (upper case);</li>
 *   <li>then the usual {@code -D<param>}, {@code CRESCO_<param>}, plugin config.</li>
 * </ol>
 * A secret taken from a file or the upper-case environment variable is not copied into the config
 * map (so {@link #getConfigMap()} does not carry it). Secret values are never logged; the source is
 * logged once per parameter. Non-secret parameters behave exactly as before.
 *
 * @author V.K. Cody Bumgardner
 * @author Caylin Hickey
 * @since 0.1.0
 */
public class Config {
    /** Environmental Variable Prefix */
    private static final String ENV_PREFIX = "CRESCO_";

    private static final Logger log = LoggerFactory.getLogger(Config.class);

    /** Test hook: when set, receives every message Config logs (in addition to the logger). */
    static volatile Consumer<String> logListener;

    private AtomicBoolean lockConfig = new AtomicBoolean();
    /** Plugin configuration object */
    protected Map<String,Object> configMap;
    /** Environment lookup: System::getenv in production. */
    private final Function<String,String> env;
    /** Secret file reads, per param: the path read and its content (read once; a restart re-reads). */
    private final Map<String,String[]> secretFileCache = new ConcurrentHashMap<>();
    /** Params whose secret source has been logged already. */
    private final Set<String> secretSourceLogged = ConcurrentHashMap.newKeySet();
    /**
     * Constructor
     * @param configMap     Plugin configuration object
     */
    public Config(Map<String,Object> configMap) {
        this(configMap, System::getenv);
    }

    /** For tests: {@code env} stands in for {@link System#getenv(String)}. */
    Config(Map<String,Object> configMap, Function<String,String> env) {
        this.env = env;
        this.configMap = Collections.synchronizedMap(new HashMap<>());
        this.configMap.putAll(configMap);
    }
    /**
     * Grab configuration entry as Boolean
     * @param param             Entry name to retrieve
     * @return                  Value of entry, null if missing
     */
    public Boolean getBooleanParam(String param) {


        String env = System.getProperty(param);

        if(env == null) {
            env = this.env.apply(ENV_PREFIX + param);
        }

        if (env != null) {

            synchronized (lockConfig) {
                configMap.put(param,env);
            }

            if (env.toLowerCase().trim().equals("true") || env.trim().equals("1")) {
                return true;
            }
            if (env.toLowerCase().trim().equals("false") || env.trim().equals("0")) {
                return false;
            }
        }

        try {

            if(configMap.containsKey(param)) {
                return Boolean.parseBoolean((String)configMap.get(param));
            } else {
                return null;
            }
        } catch (NoSuchElementException e) {
            return null;
        }

    }
    /**
     * Grab configuration entry as Boolean
     * @param param             Entry name to retrieve
     * @param ifNull            Default value to return on error
     * @return                  Value of entry, ifNull value on error
     */
    public Boolean getBooleanParam(String param, Boolean ifNull) {
        Boolean ret = getBooleanParam(param);
        if (ret != null)
            return ret;
        return ifNull;
    }
    /**
     * Grab configuration entry as Double
     * @param param             Entry name to retrieve
     * @return                  Value of entry, null if missing
     */
    public Double getDoubleParam(String param) {
        try {
            /*String env = System.getProperty(param);
            if(env == null) {
                env = System.getenv(ENV_PREFIX + param);
            }

            if(env != null) {
                synchronized (lockConfig) {
                    configMap.put(param,env);
                }
            }*/
            String env = getStringParam(param);

            return env == null ? null : Double.parseDouble(env);
        } catch (NumberFormatException nfe) {
            try {
                if(configMap.containsKey(param)) {
                    return Double.parseDouble((String) configMap.get(param));
                } else {
                    return null;
                }

            } catch (NoSuchElementException nsee) {
                return null;
            }
        }
    }
    /**
     * Grab configuration entry as Double
     * @param param             Entry name to retrieve
     * @param ifNull            Default value to return on error
     * @return                  Value of entry, ifNull value on error
     */
    public Double getDoubleParam(String param, Double ifNull) {
        Double ret = getDoubleParam(param);
        if (ret != null)
            return ret;
        return ifNull;
    }
    /**
     * Grab configuration entry as Integer
     * @param param             Entry name to retrieve
     * @return                  Value of entry, null if missing
     */
    public Integer getIntegerParam(String param) {
        try {
            /*String env = System.getProperty(param);
            if(env == null) {
                env = System.getenv(ENV_PREFIX + param);
            }

            if(env != null) {
                synchronized (lockConfig) {
                    configMap.put(param,env);
                }
            }*/
            String env = getStringParam(param);

            return env == null ? null : Integer.parseInt(env);
        } catch (NumberFormatException nfe) {
            try {
                if(configMap.containsKey(param)) {
                    return Integer.parseInt((String) configMap.get(param));
                } else {
                    return null;
                }

            } catch (NoSuchElementException nsee) {
                return null;
            }
        }
    }
    /**
     * Grab configuration entry as Integer
     * @param param             Entry name to retrieve
     * @param ifNull            Default value to return on error
     * @return                  Value of entry, ifNull value on error
     */
    public Integer getIntegerParam(String param, Integer ifNull) {
        Integer ret = getIntegerParam(param);
        if (ret != null)
            return ret;
        return ifNull;
    }
    /**
     * Grab configuration entry as Long
     * @param param             Entry name to retrieve
     * @return                  Value of entry, null if missing
     */
    public Long getLongParam(String param) {
        try {
            /*String env = System.getProperty(param);
            if(env == null) {
                env = System.getenv(ENV_PREFIX + param);
            }

            if(env != null) {
                synchronized (lockConfig) {
                    configMap.put(param,env);
                }
            }*/
            String env = getStringParam(param);

            return env == null ? null : Long.parseLong(env);
        } catch (NumberFormatException nfe) {
            try {
                if(configMap.containsKey(param)) {
                    return Long.parseLong((String)(configMap.get(param)));
                } else {
                    return null;
                }
            } catch (NoSuchElementException nsee) {
                return null;
            }
        }
    }
    /**
     * Grab configuration entry as Long
     * @param param             Entry name to retrieve
     * @param ifNull            Default value to return on error
     * @return                  Value of entry, ifNull value on error
     */
    public Long getLongParam(String param, Long ifNull) {
        Long ret = getLongParam(param);
        if (ret != null)
            return ret;
        return ifNull;
    }
    /**
     * Grab configuration entry as String
     * @param param             Entry name to retrieve
     * @return                  Value of entry, null if missing
     */
    public String getStringParam(String param) {

        if (SecretParams.isSecretParam(param)) {
            String secret = secretFromFileOrEnv(param);
            if (secret != null) {
                return secret;
            }
        }

        String env = System.getProperty(param);
        if(env == null) {
            env = this.env.apply(ENV_PREFIX + param);
        }

        if(env != null) {
            synchronized (lockConfig) {
                configMap.put(param,env);
            }
            return env;
        }

        try {
            if(configMap.containsKey(param)) {
                return (String)configMap.get(param);
            } else {
                return null;
            }
        } catch (NoSuchElementException e) {
            return null;
        }
    }
    /**
     * The secret {@code param} from its {@code <param>_file} or the {@code CRESCO_<PARAM>}
     * environment variable, or null when neither is configured (the caller falls through to the
     * usual sources). Throws IllegalStateException when a configured file fails a check.
     */
    private String secretFromFileOrEnv(String param) {
        String fileParam = param + SecretParams.FILE_SUFFIX;
        String upperEnv = ENV_PREFIX + param.toUpperCase(Locale.ROOT);

        String path = System.getProperty(fileParam);
        String from = "-D" + fileParam;
        if (path == null) { path = env.apply(upperEnv + "_FILE"); from = upperEnv + "_FILE"; }
        if (path == null) { path = env.apply(ENV_PREFIX + fileParam); from = ENV_PREFIX + fileParam; }
        if (path == null) {
            Object v = configMap.get(fileParam);
            if (v != null) { path = String.valueOf(v); from = fileParam + " (plugin config)"; }
        }

        if (path != null && !path.isBlank()) {
            String p = path.trim();
            String[] cached = secretFileCache.get(param);
            if (cached != null && cached[0].equals(p)) {
                return cached[1];
            }
            String value;
            try {
                value = OwnerOnlyFile.read(Paths.get(p), fileParam);
            } catch (OwnerOnlyFile.UnsafeFileException | java.nio.file.InvalidPathException e) {
                throw new IllegalStateException("secret " + param + " from " + from + ": " + e.getMessage(), e);
            }
            secretFileCache.put(param, new String[]{p, value});
            noteSource(param, "file " + p + " (" + from + ")");
            return value;
        }

        String value = env.apply(upperEnv);
        if (value != null) {
            noteSource(param, "environment " + upperEnv);
        }
        return value;
    }

    /** Log, once per param, where a secret came from (never its value), and a -D copy it overrides. */
    private void noteSource(String param, String source) {
        if (!secretSourceLogged.add(param)) {
            return;
        }
        note(false, "secret " + param + " taken from " + source);
        if (System.getProperty(param) != null) {
            note(true, "-D" + param + " is set but ignored (" + source + " wins); take it off the JVM command line,"
                    + " where every local user can read it");
        }
    }

    private static void note(boolean warn, String msg) {
        if (warn) log.warn(msg); else log.info(msg);
        Consumer<String> l = logListener;
        if (l != null) l.accept(msg);
    }

    /**
     * Grab configuration entry as String
     * @param param             Entry name to retrieve
     * @param ifNull            Default value to return on error
     * @return                  Value of entry, ifNull value on error
     */
    public String getStringParam(String param, String ifNull) {
        String ret = getStringParam(param);
        if (ret != null)
            return ret;
        return ifNull;
    }
    /**
     * Returns the underlying configuration object
     * @return                  The underlying configuration object
     */

    /**
     * Returns a JSON representation of the configuration object
     * @return                  JSONified configuration object
     */

    public Map<String,Object> getConfigMap() {

        Map<String,Object> returnConfigMap = new HashMap<>();
        synchronized (lockConfig) {
            returnConfigMap.putAll(this.configMap);
        }
        return returnConfigMap;
    }

    public String getConfigAsJSON() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        // NOTE: previously cast each Map.Entry to String (guaranteed ClassCastException on any
        // non-empty config). Iterate entries correctly.
        for (Map.Entry<String, Object> e : this.configMap.entrySet()) {
            sb.append("\"").append(e.getKey()).append("\":\"")
              .append(String.valueOf(e.getValue())).append("\",");
        }
        if (sb.lastIndexOf(",") > -1)
            sb.deleteCharAt(sb.lastIndexOf(","));
        sb.append("}");
        return sb.toString();
    }


}