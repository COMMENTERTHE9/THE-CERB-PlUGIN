package cerberus.world.cerb;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/**
 * Config files the plugin writes on first install (abilities.yml, classes.yml) belong to the server
 * afterwards: an update never replaces them, so a change to the shipped numbers would never reach a
 * server installed earlier. A file nobody edited is still an exact copy of a version the plugin
 * shipped (line endings aside); such a copy is replaced by the current version, the old one kept
 * next to it as {@code <name>.old}. A file someone edited is never touched.
 */
public final class ShippedConfig {

    private ShippedConfig() {}

    /**
     * SHA-256 of every earlier shipped version (text with '\r' removed), per file. When a shipped
     * file changes, the version it replaces goes here (ShippedConfigTest says so).
     */
    static final Map<String, Set<String>> EARLIER = Map.of(
            "abilities.yml", Set.of(
                    "80f9a56fe921477b71920c775c8e56566bbe6e55f7fee42a015d2ab0cdf8102e",  // Step 5
                    "a16c0eb2f03daf044144d9a55340623290adcdf8040d6daeae4bdb87e4ffc659",  // Step 5 review fixes
                    "b97549d20864c35de70aee2f7153a6bd1df0fbb652a4ebdf28162c860d448862"), // Phase 3
            "classes.yml", Set.of(
                    "c1531ecb45d585cdaed23789845b4b21314956a5f9da9a3736cd35145e22244d",  // Step 5
                    "9f6ff81a335d75f7194ab463e1f6faf9d9a3e26b03db897bf9173ac34c4a5c84")); // Ability controls 2.0

    /** What {@link #refresh} did. */
    public enum Result { MISSING, CURRENT, REFRESHED, EDITED }

    /**
     * Replace {@code file} with {@code shipped} when it is an unedited copy of an earlier version
     * (its hash in {@code earlier}); the old copy is kept as {@code <name>.old}. Anything else is
     * left as it is.
     */
    public static Result refresh(File file, byte[] shipped, Set<String> earlier) throws IOException {
        if (!file.isFile()) return Result.MISSING;
        String have = hash(Files.readAllBytes(file.toPath()));
        if (have.equals(hash(shipped))) return Result.CURRENT;
        if (earlier == null || !earlier.contains(have)) return Result.EDITED;
        File old = new File(file.getParentFile(), file.getName() + ".old");
        Files.copy(file.toPath(), old.toPath(), StandardCopyOption.REPLACE_EXISTING);
        File next = new File(file.getParentFile(), file.getName() + ".new");
        Files.write(next.toPath(), shipped);
        Files.move(next.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); // never half-written
        return Result.REFRESHED;
    }

    /** {@link #refresh} for one of the plugin's own files, with its shipped copy from the jar. */
    static Result refresh(File dataFolder, String name, InputStream shipped) throws IOException {
        if (shipped == null) return Result.MISSING;
        try (InputStream in = shipped) {
            return refresh(new File(dataFolder, name), in.readAllBytes(), EARLIER.get(name));
        }
    }

    /** SHA-256 hex of the text with '\r' removed (a Windows checkout ships CRLF line endings). */
    static String hash(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8).replace("\r", "");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every JVM has SHA-256
        }
    }
}
