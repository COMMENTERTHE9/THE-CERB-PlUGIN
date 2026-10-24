package cerberus.world.cerb;

import java.io.InputStream;
import java.util.Properties;

/** Build stamp written by Maven resource filtering (cerberus-build.properties). */
public final class BuildInfo {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = BuildInfo.class.getClassLoader().getResourceAsStream("cerberus-build.properties")) {
            if (in != null) PROPS.load(in);
        } catch (Exception ignored) {
            // shown as "unknown"
        }
    }

    private BuildInfo() {}

    /** When this jar was built, e.g. "2026-10-24 20:41 UTC". */
    public static String buildTime() {
        String v = PROPS.getProperty("build.time", "unknown");
        return v.startsWith("${") ? "unknown" : v;
    }
}
