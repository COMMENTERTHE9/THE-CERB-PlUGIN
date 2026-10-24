package Manager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-key save throttle with trailing-edge coalescing (pure, thread-safe, unit-tested).
 *
 * <p>The first request for a key runs immediately. Requests inside the cooldown are not dropped:
 * the newest one is kept and must run once the cooldown ends. The old throttle dropped them,
 * so the last XP gains of a burst were only saved at quit/shutdown and lost on a crash.
 */
public final class SaveThrottle {

    /** What the caller should do with a save request. */
    public enum Action { RUN_NOW, SCHEDULE_FLUSH, ALREADY_SCHEDULED }

    /** Decision plus, for {@link Action#SCHEDULE_FLUSH}, how long to wait. */
    public record Decision(Action action, long delayMs) {}

    private final long cooldownMs;
    private final Map<String, Long> lastRun = new ConcurrentHashMap<>();
    private final Map<String, Boolean> flushScheduled = new ConcurrentHashMap<>();

    public SaveThrottle(long cooldownMs) {
        this.cooldownMs = Math.max(0, cooldownMs);
    }

    /** Register a save request for {@code key} at time {@code now}. */
    public Decision request(String key, long now) {
        synchronized (lockFor(key)) {
            Long last = lastRun.get(key);
            if (last == null || now - last >= cooldownMs) {
                if (Boolean.TRUE.equals(flushScheduled.get(key))) {
                    return new Decision(Action.ALREADY_SCHEDULED, 0); // pending flush will run newest
                }
                lastRun.put(key, now);
                return new Decision(Action.RUN_NOW, 0);
            }
            if (Boolean.TRUE.equals(flushScheduled.get(key))) {
                return new Decision(Action.ALREADY_SCHEDULED, 0);
            }
            flushScheduled.put(key, true);
            return new Decision(Action.SCHEDULE_FLUSH, Math.max(1, cooldownMs - (now - last)));
        }
    }

    /** Call when a scheduled flush runs; records it as the key's latest run. */
    public void flushed(String key, long now) {
        synchronized (lockFor(key)) {
            flushScheduled.remove(key);
            lastRun.put(key, now);
        }
    }

    /** Per-key lock shared with callers that must update related state atomically. */
    static Object lockFor(String key) {
        return key.intern();
    }
}
