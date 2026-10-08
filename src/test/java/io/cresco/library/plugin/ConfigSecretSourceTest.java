package io.cresco.library.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** OUT-03b: plugin secret params from an owner-only {@code <param>_file} or {@code CRESCO_<PARAM>}, ahead of -D. */
class ConfigSecretSourceTest {

    private Path tmp;
    private final List<String> logged = Collections.synchronizedList(new ArrayList<>());
    private final List<String> sysProps = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        tmp = Files.createTempDirectory("config-secret");
        Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rwx------"));
        Config.logListener = logged::add;
    }

    @AfterEach
    void tearDown() throws IOException {
        Config.logListener = null;
        for (String p : sysProps) System.clearProperty(p);
        try (Stream<Path> w = Files.walk(tmp)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private void sys(String k, String v) {
        sysProps.add(k);
        System.setProperty(k, v);
    }

    private Path secretFile(String name, String content, String mode) throws IOException {
        Path f = tmp.resolve(name);
        Files.write(f, content.getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(f, PosixFilePermissions.fromString(mode));
        return f;
    }

    private static Config config(Map<String, Object> map, String... envKv) {
        Map<String, String> env = new HashMap<>();
        for (int i = 0; i < envKv.length; i += 2) env.put(envKv[i], envKv[i + 1]);
        return new Config(map, env::get);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private void assertNotLogged(String... values) {
        for (String line : logged) for (String v : values) assertFalse(line.contains(v), "a log line leaked a secret: " + line);
    }

    @Test
    void theRuleIsTheControllers() {
        for (String p : new String[]{"wsapi_keystore_password", "cresco_service_key", "keystorepwd", "truststorepwd",
                "db_password", "broker_security_secret", "discovery_secret_region", "api_token", "hsm_pin", "hsmPin"}) {
            assertTrue(SecretParams.isSecretParam(p), p);
        }
        for (String p : new String[]{"region_name", "wsapi_port", "wsapi_keystore_password_file", "db_key", "db_key_file",
                "gfs_secret", "gfs_secret_env", "gfs_secret_previous_files", "core_master_key", "tape_media_key",
                "gfs_pkcs11_pin", "ping_interval", "mapping", "core_tofu_pinning", "", null}) {
            assertFalse(SecretParams.isSecretParam(p), String.valueOf(p));
        }
    }

    @Test
    void aFileWinsOverTheCommandLine() throws Exception {
        Path f = secretFile("ks", "from-file-s3cr3t\n", "rw-------");
        sys("wsapi_keystore_password", "from-cmdline");
        sys("wsapi_keystore_password_file", f.toString());
        Config c = config(map("wsapi_keystore_password", "from-config"), "CRESCO_WSAPI_KEYSTORE_PASSWORD", "from-env");
        assertEquals("from-file-s3cr3t", c.getStringParam("wsapi_keystore_password"));
        assertEquals("from-file-s3cr3t", c.getStringParam("wsapi_keystore_password", "default"));
        assertEquals("from-config", c.getConfigMap().get("wsapi_keystore_password"), "the file value is not copied into the config map");
        assertTrue(logged.stream().anyMatch(l -> l.startsWith("secret wsapi_keystore_password taken from file")), logged.toString());
        assertTrue(logged.stream().anyMatch(l -> l.startsWith("-Dwsapi_keystore_password is set but ignored")), logged.toString());
        assertNotLogged("from-file-s3cr3t", "from-cmdline", "from-env", "from-config");
    }

    @Test
    void theUpperCaseEnvironmentVariableWinsOverTheCommandLine() {
        sys("cresco_service_key", "from-cmdline");
        Config c = config(map("cresco_service_key", "from-config"),
                "CRESCO_CRESCO_SERVICE_KEY", "from-env", "CRESCO_cresco_service_key", "exact-case");
        assertEquals("from-env", c.getStringParam("cresco_service_key"));
        assertTrue(logged.stream().anyMatch(l -> l.equals("secret cresco_service_key taken from environment CRESCO_CRESCO_SERVICE_KEY")), logged.toString());
        assertNotLogged("from-env", "from-cmdline", "from-config", "exact-case");
    }

    @Test
    void theFilePathCanComeFromTheEnvironmentOrThePluginConfigAndBeatsTheEnvironmentValue() throws Exception {
        Path f1 = secretFile("a", "file-one", "r--------");
        Path f2 = secretFile("b", "file-two", "rw-------");
        Path f3 = secretFile("c", "file-three", "rw-------");
        assertEquals("file-one", config(map(), "CRESCO_DB_PASSWORD_FILE", f1.toString(), "CRESCO_DB_PASSWORD", "env").getStringParam("db_password"));
        assertEquals("file-two", config(map("db_password_file", f2.toString(), "db_password", "cfg")).getStringParam("db_password"));
        assertEquals("file-three", config(map(), "CRESCO_db_password_file", f3.toString()).getStringParam("db_password"));
    }

    @Test
    void withoutAFileOrUpperCaseVariableTheExistingOrderIsUnchanged() {
        Config c = config(map("broker_security_secret", "from-config"), "CRESCO_broker_security_secret", "exact-case");
        assertEquals("exact-case", c.getStringParam("broker_security_secret"));
        assertEquals("from-config", config(map("broker_security_secret", "from-config")).getStringParam("broker_security_secret"));
        sys("broker_security_secret", "from-cmdline");
        assertEquals("from-cmdline", c.getStringParam("broker_security_secret"));
        assertNull(config(map()).getStringParam("discovery_secret_global"));
        assertEquals("dflt", config(map()).getStringParam("discovery_secret_global", "dflt"));
    }

    @Test
    void theFileIsReadOnceAndCached() throws Exception {
        Path f = secretFile("rot", "first", "rw-------");
        Config c = config(map("cresco_service_key_file", f.toString()));
        assertEquals("first", c.getStringParam("cresco_service_key"));
        Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rw-------"));
        Files.write(f, "rotated".getBytes(StandardCharsets.UTF_8));
        assertEquals("first", c.getStringParam("cresco_service_key"), "a rotated file is picked up on restart, not mid-run");
        assertEquals(1, logged.stream().filter(l -> l.startsWith("secret cresco_service_key taken from")).count(),
                "the source is logged once per Config");
        assertEquals("rotated", config(map("cresco_service_key_file", f.toString())).getStringParam("cresco_service_key"),
                "a new Config (a plugin or agent restart) reads the file again");
        assertNotLogged("first", "rotated");
    }

    @Test
    void aGroupOrWorldReadableFileIsRefused() throws Exception {
        for (String mode : new String[]{"rw-r-----", "rw-r--r--", "rw-rw-rw-"}) {
            Path f = secretFile("loose" + mode.hashCode(), "a-secret-value", mode);
            Config c = config(map("wsapi_keystore_password_file", f.toString(), "wsapi_keystore_password", "cfg-value"));
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> c.getStringParam("wsapi_keystore_password"), mode);
            assertTrue(e.getMessage().contains("wsapi_keystore_password"), e.getMessage());
            assertTrue(e.getMessage().contains("0600"), e.getMessage());
            assertFalse(e.getMessage().contains("a-secret-value"), e.getMessage());
            assertFalse(e.getMessage().contains("cfg-value"), e.getMessage());
            assertThrows(IllegalStateException.class, () -> c.getStringParam("wsapi_keystore_password", "dflt"), "refused, not defaulted");
        }
        Config missing = config(map(), "CRESCO_CRESCO_SERVICE_KEY_FILE", tmp.resolve("missing").toString());
        assertThrows(IllegalStateException.class, () -> missing.getStringParam("cresco_service_key"));
        Path real = secretFile("real", "a-secret-value", "rw-------");
        Path link = Files.createSymbolicLink(tmp.resolve("link"), real);
        assertThrows(IllegalStateException.class, () -> config(map("db_password_file", link.toString())).getStringParam("db_password"));
        assertNotLogged("a-secret-value", "cfg-value");
    }

    @Test
    @Disabled("a file owned by another user needs root (chown) to set up; not portable. OwnerOnlyFile refuses it by owner name.")
    void aFileOwnedByAnotherUserIsRefused() {
    }

    @Test
    void nonSecretParamsAreUnchanged() throws Exception {
        Path f = secretFile("rn", "from-file", "rw-------");
        Config c = config(map("region_name", "from-config", "region_name_file", f.toString()),
                "CRESCO_REGION_NAME", "upper-env", "CRESCO_REGION_NAME_FILE", f.toString());
        assertEquals("from-config", c.getStringParam("region_name"), "no file or upper-case env for a non-secret");
        Config e = config(map("region_name", "from-config"), "CRESCO_region_name", "exact-case");
        assertEquals("exact-case", e.getStringParam("region_name"));
        assertEquals("exact-case", e.getConfigMap().get("region_name"), "as before, an env/-D value is copied into the map");
        sys("region_name", "from-cmdline");
        assertEquals("from-cmdline", e.getStringParam("region_name"));
        assertEquals("from-cmdline", e.getConfigMap().get("region_name"));
        // a loose file next to a non-secret param is not even looked at
        Path loose = secretFile("loose", "x", "rw-r--r--");
        assertEquals("v", config(map("wsapi_port", "v", "wsapi_port_file", loose.toString())).getStringParam("wsapi_port"));
        assertEquals(Integer.valueOf(8282), config(map("wsapi_port", "8282")).getIntegerParam("wsapi_port"));
        assertEquals(Boolean.TRUE, config(map(), "CRESCO_enable_x", "true").getBooleanParam("enable_x"));
        assertTrue(logged.isEmpty(), logged.toString());
    }

    @Test
    void gfsKeyFamiliesKeepTheirOwnLoaders() throws Exception {
        // gfs reads gfs_secret_file itself (KeySources) and refuses two sources: the library must not read it too
        Path f = secretFile("gfs", "kid:abc", "rw-------");
        Config c = config(map("gfs_secret_file", f.toString()), "CRESCO_GFS_SECRET", "upper-env");
        assertNull(c.getStringParam("gfs_secret"));
        assertEquals(f.toString(), c.getStringParam("gfs_secret_file"));
        assertNull(c.getStringParam("core_master_key"));
    }
}
