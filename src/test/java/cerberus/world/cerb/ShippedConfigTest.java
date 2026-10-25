package cerberus.world.cerb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShippedConfigTest {

    /**
     * The shipped files as this version has them. When one changes, its old hash here goes into
     * ShippedConfig.EARLIER (so servers holding an unedited copy get the new one) and the new hash
     * replaces it here.
     */
    private static final String CURRENT_ABILITIES = "b838aacb331535782ae0bb1e06f7732b71a688090ddfd58ee227b3ee71ed7de2";
    private static final String CURRENT_CLASSES = "c3cdfeeb78fb2427fea2f5a297a9dd1c963c5a79d11cefd614525c2648c06860";

    @Test
    void everyShippedChangeKeepsTheVersionItReplaces() throws Exception {
        check("abilities.yml", CURRENT_ABILITIES);
        check("classes.yml", CURRENT_CLASSES);
    }

    private static void check(String name, String expected) throws Exception {
        String now = ShippedConfig.hash(Files.readAllBytes(Path.of("src/main/resources", name)));
        assertEquals(expected, now, name + " changed: add " + expected + " to ShippedConfig.EARLIER and set its hash in this test to " + now);
        assertFalse(ShippedConfig.EARLIER.get(name).contains(now), name + "'s current version is not an earlier one");
    }

    @Test
    void anUneditedEarlierCopyIsReplacedAndKept(@TempDir Path dir) throws Exception {
        byte[] earlier = "a: 1\nb: 2\n".getBytes(StandardCharsets.UTF_8);
        byte[] shipped = "a: 1\nb: 3\n".getBytes(StandardCharsets.UTF_8);
        Set<String> known = Set.of(ShippedConfig.hash(earlier));
        File f = dir.resolve("abilities.yml").toFile();

        assertEquals(ShippedConfig.Result.MISSING, ShippedConfig.refresh(f, shipped, known));

        Files.writeString(f.toPath(), "a: 1\r\nb: 2\r\n"); // written by a Windows build: CRLF
        assertEquals(ShippedConfig.Result.REFRESHED, ShippedConfig.refresh(f, shipped, known));
        assertArrayEquals(shipped, Files.readAllBytes(f.toPath()));
        assertEquals("a: 1\r\nb: 2\r\n", Files.readString(dir.resolve("abilities.yml.old")), "the old copy is kept as it was");
        assertFalse(dir.resolve("abilities.yml.new").toFile().exists());

        assertEquals(ShippedConfig.Result.CURRENT, ShippedConfig.refresh(f, shipped, known), "then it is current");
        Files.writeString(f.toPath(), "a: 1\r\nb: 3\r\n");
        assertEquals(ShippedConfig.Result.CURRENT, ShippedConfig.refresh(f, shipped, known), "line endings don't count");
        assertEquals("a: 1\r\nb: 3\r\n", Files.readString(f.toPath()), "...and a current copy is left alone");
    }

    @Test
    void aFileWithTheOwnersEditsIsNeverTouched(@TempDir Path dir) throws Exception {
        byte[] earlier = "a: 1\n".getBytes(StandardCharsets.UTF_8);
        File f = dir.resolve("classes.yml").toFile();
        Files.writeString(f.toPath(), "a: 1\n# my note\n");
        assertEquals(ShippedConfig.Result.EDITED,
                ShippedConfig.refresh(f, "a: 2\n".getBytes(StandardCharsets.UTF_8), Set.of(ShippedConfig.hash(earlier))));
        assertEquals("a: 1\n# my note\n", Files.readString(f.toPath()));
        assertFalse(dir.resolve("classes.yml.old").toFile().exists());
        assertEquals(ShippedConfig.Result.EDITED, ShippedConfig.refresh(f, "a: 2\n".getBytes(StandardCharsets.UTF_8), null), "no history: kept");
    }

    @Test
    void theSandboxCopiesOfEarlierVersionsAreRecognised() {
        // The hashes are of the text without '\r': the same for a Linux and a Windows checkout.
        assertEquals(ShippedConfig.hash("x\n".getBytes(StandardCharsets.UTF_8)), ShippedConfig.hash("x\r\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals(3, ShippedConfig.EARLIER.get("abilities.yml").size());
        assertEquals(2, ShippedConfig.EARLIER.get("classes.yml").size());
    }
}
