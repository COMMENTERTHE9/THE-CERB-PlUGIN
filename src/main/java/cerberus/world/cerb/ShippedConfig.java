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
 * Config files the plugin writes on first install (abilities.yml, classes.yml, attributes.yml,
 * resources.yml) belong to the server
 * afterwards: an update never replaces them, so a change to the shipped numbers would never reach a
 * server installed earlier. A file nobody edited is still an exact copy of a version the plugin
 * shipped (line endings aside); such a copy is replaced by the current version, the old one kept
 * next to it as {@code <name>.old} (or {@code .old.2}, {@code .old.3}... when that name is taken:
 * an existing backup is never overwritten). A file someone edited is never touched.
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
                    "b97549d20864c35de70aee2f7153a6bd1df0fbb652a4ebdf28162c860d448862",  // Phase 3
                    "b838aacb331535782ae0bb1e06f7732b71a688090ddfd58ee227b3ee71ed7de2"), // the movement rule, first cut
            "classes.yml", Set.of(
                    "c1531ecb45d585cdaed23789845b4b21314956a5f9da9a3736cd35145e22244d",  // Step 5
                    "9f6ff81a335d75f7194ab463e1f6faf9d9a3e26b03db897bf9173ac34c4a5c84",  // Ability controls 2.0
                    "c3cdfeeb78fb2427fea2f5a297a9dd1c963c5a79d11cefd614525c2648c06860"), // before class colours
            "attributes.yml", Set.of(
                    "86570131ff02bbae03d48a40992b786e595dc77ceea83eebd4e68434cf3adc23",  // Kill XP v2
                    "290bb31fdfa52a76472b81c867b20ef43a04d69f1bbc00426f2807ec6ad4ce37",  // Attributes engine core
                    "6946f28a15609bfac0f0e36c49d08ca105748ad4545b213f762c5150bbc6850c",  // admin inspector
                    "451216560bb8f39b03131cbc84377ea6485a24c601ff7e79b54bc4d5edde4edf",  // realm magnitude
                    "ab9ecdf0e6d78a9824828546b58150a10f35299241db3f9724e7bed61fb843ab",  // speed and handling stats
                    "cb17940f48676bdf2543988f01131f3f34e012ec648da447d817206643291fd2",  // owner decisions (75% cap)
                    "ad4e8c0fd68ce07a064242a817a43fb490c6d22e63423e19ca057c1f191eb01f",  // PvP off for now, not permanently
                    "732ea757732da583653629827eaef11ae7f585e945d7b18a7cf016a00229a95b",  // Mining: gathering speed, Fortune
                    "694fa4e289d731d8e1fd1332e678d74347092d48e66fa5ef23af42753910c373"), // XP integrity
            "resources.yml", Set.of(
                    "994d99e195a284ffcd94a66de46f7ac631c90837aa517423d6ce8528fcf55fd9",  // the gathering loop, first cut
                    "2c2e3135edd9c620ad124edbeaa4be107a9fae3150457b087d49524fcdc8a942")); // the gathering loop, as shipped

    /** What {@link #refresh} did. */
    public enum Result { MISSING, CURRENT, REFRESHED, EDITED }

    /** {@link #refresh}'s outcome, with the backup's file name when it refreshed. */
    public record Outcome(Result result, String backup) {
        static final Outcome MISSING = new Outcome(Result.MISSING, null);
        static final Outcome CURRENT = new Outcome(Result.CURRENT, null);
        static final Outcome EDITED = new Outcome(Result.EDITED, null);
    }

    /**
     * Replace {@code file} with {@code shipped} when it is an unedited copy of an earlier version
     * (its hash in {@code earlier}); the old copy is kept under the first free name of
     * {@code <name>.old}, {@code <name>.old.2}... Anything else is left as it is.
     */
    public static Outcome refresh(File file, byte[] shipped, Set<String> earlier) throws IOException {
        if (!file.isFile()) return Outcome.MISSING;
        String have = hash(Files.readAllBytes(file.toPath()));
        if (have.equals(hash(shipped))) return Outcome.CURRENT;
        if (earlier == null || !earlier.contains(have)) return Outcome.EDITED;
        File old = backupName(file);
        if (old == null) return Outcome.EDITED; // a hundred backups already: leave everything alone
        Files.copy(file.toPath(), old.toPath()); // fails rather than overwrite, should the name be taken meanwhile
        File next = new File(file.getParentFile(), file.getName() + ".new");
        Files.write(next.toPath(), shipped);
        try { // never half-written
            Files.move(next.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(next.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return new Outcome(Result.REFRESHED, old.getName());
    }

    /** The first free backup name: {@code <name>.old}, then {@code <name>.old.2} up to {@code .old.100}; null when none is free. */
    static File backupName(File file) {
        File first = new File(file.getParentFile(), file.getName() + ".old");
        if (!first.exists()) return first;
        for (int n = 2; n <= 100; n++) {
            File f = new File(file.getParentFile(), file.getName() + ".old." + n);
            if (!f.exists()) return f;
        }
        return null;
    }

    /** {@link #refresh} for one of the plugin's own files, with its shipped copy from the jar. */
    static Outcome refresh(File dataFolder, String name, InputStream shipped) throws IOException {
        if (shipped == null) return Outcome.MISSING;
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
