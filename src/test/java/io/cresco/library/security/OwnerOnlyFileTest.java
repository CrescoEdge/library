package io.cresco.library.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The owner-only file rules: 0600/0400, regular file, safe directory; errors never carry the content. */
class OwnerOnlyFileTest {

    private Path tmp;

    @BeforeEach
    void setUp() throws IOException {
        tmp = Files.createTempDirectory("owner-only");
        Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rwx------"));
    }

    @AfterEach
    void tearDown() throws IOException {
        try (Stream<Path> w = Files.walk(tmp)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private Path file(Path dir, String name, String content, String mode) throws IOException {
        Path f = dir.resolve(name);
        Files.write(f, content.getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(f, PosixFilePermissions.fromString(mode));
        return f;
    }

    @Test
    void ownerOnlyFilesAreReadAndStripped() throws Exception {
        assertEquals("s3cr3t", OwnerOnlyFile.read(file(tmp, "a", "s3cr3t\n", "rw-------"), "a_file"));
        assertEquals("s3cr3t", OwnerOnlyFile.read(file(tmp, "b", "  s3cr3t  ", "r--------"), "b_file"));
    }

    @Test
    void groupOrWorldReadableModesAreRefused() throws Exception {
        for (String mode : new String[]{"rw-r-----", "rw----r--", "rw-rw-rw-", "rwx------"}) {
            Path f = file(tmp, "m" + mode.hashCode(), "a-secret-value", mode);
            OwnerOnlyFile.UnsafeFileException e = assertThrows(OwnerOnlyFile.UnsafeFileException.class,
                    () -> OwnerOnlyFile.read(f, "x_file"), mode);
            assertTrue(e.getMessage().contains("0600"), e.getMessage());
            assertFalse(e.getMessage().contains("a-secret-value"));
        }
    }

    @Test
    void symlinksMissingFilesDirectoriesAndUnsafeDirectoriesAreRefused() throws Exception {
        Path real = file(tmp, "real", "a-secret-value", "rw-------");
        Path link = Files.createSymbolicLink(tmp.resolve("link"), real);
        assertThrows(OwnerOnlyFile.UnsafeFileException.class, () -> OwnerOnlyFile.read(link, "x_file"));
        assertThrows(OwnerOnlyFile.UnsafeFileException.class, () -> OwnerOnlyFile.read(tmp.resolve("missing"), "x_file"));
        assertThrows(OwnerOnlyFile.UnsafeFileException.class, () -> OwnerOnlyFile.read(tmp, "x_file"));
        assertThrows(OwnerOnlyFile.UnsafeFileException.class, () -> OwnerOnlyFile.read(null, "x_file"));

        Path open = Files.createDirectory(tmp.resolve("open"));
        Path f = file(open, "s", "a-secret-value", "rw-------");
        Files.setPosixFilePermissions(open, PosixFilePermissions.fromString("rwxrwxrwx"));
        OwnerOnlyFile.UnsafeFileException e = assertThrows(OwnerOnlyFile.UnsafeFileException.class, () -> OwnerOnlyFile.read(f, "x_file"));
        assertTrue(e.getMessage().contains("world-writable"), e.getMessage());
        assertFalse(e.getMessage().contains("a-secret-value"));
    }

    // A file owned by another user needs root (chown) to set up, so that case is not tested here.
}
