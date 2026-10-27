package Listener;

import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Trident;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which weapon a shot's kill trains (the owner's report, 2026-10-27): a crossbow's firework rocket that killed after a
 * switch to a sword trained Blade Mastery, because rockets carried no credit and the kill fell back to the item in hand.
 */
class ShotCreditTest {

    /** A projectile of {@code type} answering {@code answers} by method name (anything else: null, false or 0). */
    private static <T extends Projectile> T shot(Class<T> type, Map<String, Object> answers) {
        Object p = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, m, args) -> {
            if (answers.containsKey(m.getName())) return answers.get(m.getName());
            return zero(m.getReturnType());
        });
        return type.cast(p);
    }

    private static Object zero(Class<?> r) {
        if (r == boolean.class) return false;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        if (r == double.class) return 0.0;
        if (r == float.class) return 0f;
        return null;
    }

    @Test
    void aCrossbowsRocketIsTheCrossbows() {
        assertEquals("CROSSBOW", VirtualHealthListener.firedBy(shot(Firework.class, Map.of("isShotAtAngle", true))));
    }

    @Test
    void aRocketUsedOnABlockOrForAnElytraBoostIsNoWeapons() {
        assertNull(VirtualHealthListener.firedBy(shot(Firework.class, Map.of("isShotAtAngle", false))));
    }

    @Test
    void aThrownTridentIsTheTridentsNotABows() {
        assertEquals("TRIDENT", VirtualHealthListener.firedBy(shot(Trident.class, Map.of())));
    }

    @Test
    void anArrowIsItsWeaponsAndOneNothingFiredIsABows() {
        assertEquals("BOW", VirtualHealthListener.firedBy(shot(Arrow.class, Map.of())), "no weapon recorded (a command's arrow)");
        assertEquals("CROSSBOW", VirtualHealthListener.arrowWeapon(Material.CROSSBOW));
        assertEquals("BOW", VirtualHealthListener.arrowWeapon(Material.BOW));
        assertEquals("BOW", VirtualHealthListener.arrowWeapon(null));
    }

    @Test
    void aSnowballTrainsNothing() {
        assertNull(VirtualHealthListener.firedBy(shot(Snowball.class, Map.of())));
    }
}
