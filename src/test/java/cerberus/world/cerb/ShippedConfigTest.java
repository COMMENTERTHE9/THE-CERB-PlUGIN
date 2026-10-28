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
    private static final String CURRENT_ABILITIES = "677925246e61266f82d703fd4336c5a240c047f53516571e9a897624cc339830";
    private static final String CURRENT_CLASSES = "dce0224e4c4a60ab457f301d14b4bcb6b89f3b04407c24fa2ed36d9b2f1157be";
    private static final String CURRENT_ATTRIBUTES = "bc60fbe8a94a7ca4f6b2b92b677b53aad5e52cd4392684022c656841bdedf8ce";
    private static final String CURRENT_RESOURCES = "d64023d247a21a773aa3358ece0687c3820442e341442297847a0822f8cc7595";

    @Test
    void everyShippedChangeKeepsTheVersionItReplaces() throws Exception {
        check("abilities.yml", CURRENT_ABILITIES);
        check("classes.yml", CURRENT_CLASSES);
        check("attributes.yml", CURRENT_ATTRIBUTES);
        check("resources.yml", CURRENT_RESOURCES);
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

        assertEquals(ShippedConfig.Result.MISSING, ShippedConfig.refresh(f, shipped, known).result());

        Files.writeString(f.toPath(), "a: 1\r\nb: 2\r\n"); // written by a Windows build: CRLF
        ShippedConfig.Outcome done = ShippedConfig.refresh(f, shipped, known);
        assertEquals(ShippedConfig.Result.REFRESHED, done.result());
        assertEquals("abilities.yml.old", done.backup(), "the log names the backup");
        assertArrayEquals(shipped, Files.readAllBytes(f.toPath()));
        assertEquals("a: 1\r\nb: 2\r\n", Files.readString(dir.resolve("abilities.yml.old")), "the old copy is kept as it was");
        assertFalse(dir.resolve("abilities.yml.new").toFile().exists());

        assertEquals(ShippedConfig.Result.CURRENT, ShippedConfig.refresh(f, shipped, known).result(), "then it is current");
        Files.writeString(f.toPath(), "a: 1\r\nb: 3\r\n");
        assertEquals(ShippedConfig.Result.CURRENT, ShippedConfig.refresh(f, shipped, known).result(), "line endings don't count");
        assertEquals("a: 1\r\nb: 3\r\n", Files.readString(f.toPath()), "...and a current copy is left alone");
    }

    @Test
    void anExistingBackupIsNeverOverwritten(@TempDir Path dir) throws Exception {
        byte[] earlier = "a: 1\n".getBytes(StandardCharsets.UTF_8), shipped = "a: 2\n".getBytes(StandardCharsets.UTF_8);
        Set<String> known = Set.of(ShippedConfig.hash(earlier));
        File f = dir.resolve("abilities.yml").toFile();
        Files.writeString(dir.resolve("abilities.yml.old"), "the owner's tuned file\n"); // moved aside by hand earlier
        Files.write(f.toPath(), earlier);
        assertEquals(new ShippedConfig.Outcome(ShippedConfig.Result.REFRESHED, "abilities.yml.old.2"), ShippedConfig.refresh(f, shipped, known));
        assertEquals("the owner's tuned file\n", Files.readString(dir.resolve("abilities.yml.old")), "untouched");
        assertEquals("a: 1\n", Files.readString(dir.resolve("abilities.yml.old.2")), "this backup took the next free name");
        assertArrayEquals(shipped, Files.readAllBytes(f.toPath()));
        for (int n = 3; n <= 100; n++) Files.writeString(dir.resolve("abilities.yml.old." + n), "x");
        Files.write(f.toPath(), earlier);
        assertEquals(ShippedConfig.Result.EDITED, ShippedConfig.refresh(f, shipped, known).result(), "no free backup name: left alone");
        assertArrayEquals(earlier, Files.readAllBytes(f.toPath()));
    }

    @Test
    void aFileWithTheOwnersEditsIsNeverTouched(@TempDir Path dir) throws Exception {
        byte[] earlier = "a: 1\n".getBytes(StandardCharsets.UTF_8);
        File f = dir.resolve("classes.yml").toFile();
        Files.writeString(f.toPath(), "a: 1\n# my note\n");
        assertEquals(ShippedConfig.Result.EDITED,
                ShippedConfig.refresh(f, "a: 2\n".getBytes(StandardCharsets.UTF_8), Set.of(ShippedConfig.hash(earlier))).result());
        assertEquals("a: 1\n# my note\n", Files.readString(f.toPath()));
        assertFalse(dir.resolve("classes.yml.old").toFile().exists());
        assertEquals(ShippedConfig.Result.EDITED, ShippedConfig.refresh(f, "a: 2\n".getBytes(StandardCharsets.UTF_8), null).result(), "no history: kept");
    }

    @Test
    void theSandboxCopiesOfEarlierVersionsAreRecognised() {
        // The hashes are of the text without '\r': the same for a Linux and a Windows checkout.
        assertEquals(ShippedConfig.hash("x\n".getBytes(StandardCharsets.UTF_8)), ShippedConfig.hash("x\r\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals(4, ShippedConfig.EARLIER.get("abilities.yml").size());
        assertEquals(4, ShippedConfig.EARLIER.get("classes.yml").size());
    }
}
