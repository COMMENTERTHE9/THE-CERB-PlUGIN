package Classes;

import java.util.List;
import java.util.Map;

/**
 * A class: a specialization tree (classes.yml; docs/design/CLASSES.md §2). Open to anyone whose
 * skills meet its entry; never requires another class.
 *
 * @param entry      skill (internal name) -> level: all of these are needed
 * @param entryAny   skill -> level: at least one of these is needed (empty = no such condition)
 * @param abilities  its abilities, in tree order
 */
public record ClassDef(String id, String displayName, String role, String signature,
                       Map<String, Integer> entry, Map<String, Integer> entryAny, List<String> abilities) {

    /** True when skill levels ({@code level.apply(internal name)}) meet the entry. */
    public boolean canLearn(java.util.function.ToIntFunction<String> level) {
        for (Map.Entry<String, Integer> e : entry.entrySet()) if (level.applyAsInt(e.getKey()) < e.getValue()) return false;
        if (entryAny.isEmpty()) return true;
        for (Map.Entry<String, Integer> e : entryAny.entrySet()) if (level.applyAsInt(e.getKey()) >= e.getValue()) return true;
        return false;
    }
}
