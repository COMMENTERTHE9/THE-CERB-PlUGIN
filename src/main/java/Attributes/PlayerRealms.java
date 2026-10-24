package Attributes;

import Manager.Realm;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Which realm each player is in. Until Ascension (breakthroughs, `player_cultivation`) is built,
 * every player is in the configured default realm; admins can set a temporary test realm to
 * check cross-realm math in-game. Test realms are never saved and end on logout.
 */
public final class PlayerRealms {

    private final Supplier<Realm> defaultRealm;
    private final Map<UUID, Realm> testRealms = new ConcurrentHashMap<>();

    public PlayerRealms(Supplier<Realm> defaultRealm) {
        this.defaultRealm = defaultRealm;
    }

    public Realm realmOf(UUID player) {
        Realm r = testRealms.get(player);
        return r != null ? r : defaultRealm.get();
    }

    public boolean hasTestRealm(UUID player) { return testRealms.containsKey(player); }

    public void setTestRealm(UUID player, Realm realm) { testRealms.put(player, realm); }

    /** Back to the default realm. Returns true when a test realm was set. */
    public boolean clearTestRealm(UUID player) { return testRealms.remove(player) != null; }

    public Realm defaultRealm() { return defaultRealm.get(); }
}
