package Listener;

import Manager.DeathContext;

import java.util.UUID;

/**
 * Per-player session object with lifecycle state machine.
 * Owned by PlayerSessionManager in a Map&lt;UUID, PlayerSession&gt;.
 *
 * <pre>
 * States: JOINING → LOADING_DATA → ACTIVE → DEAD → RESPAWNING → ACTIVE
 *                                         ↓
 *                                    CLEANING_UP → CLOSED
 * </pre>
 *
 * Each session has a generation ID for stale-callback detection:
 * async callbacks capture the generation at dispatch time and compare
 * against the current session's generation before applying results.
 */
public final class PlayerSession {

    public enum State {
        JOINING,
        LOADING_DATA,
        ACTIVE,
        DEAD,
        RESPAWNING,
        CLEANING_UP,
        CLOSED
    }

    private final UUID uuid;
    private final long generation;       // monotonic, incremented per join
    private final long joinedAt;
    private volatile State state;
    private String customId;             // cached from DB during load — no sync lookup on quit
    private DeathContext deathContext;    // populated on death, consumed on respawn

    public PlayerSession(UUID uuid, long generation) {
        this.uuid = uuid;
        this.generation = generation;
        this.joinedAt = System.currentTimeMillis();
        this.state = State.JOINING;
    }

    /* ── getters ─────────────────────────────── */
    public UUID   getUuid()         { return uuid; }
    public long   getGeneration()   { return generation; }
    public long   getJoinedAt()     { return joinedAt; }
    public State  getState()        { return state; }
    public String getCustomId()     { return customId; }

    /* ── state transitions ───────────────────── */
    public void setState(State s)            { this.state = s; }
    public boolean isActive()                { return state == State.ACTIVE; }
    public boolean isClosed()                { return state == State.CLOSED; }

    /* ── customId cache ──────────────────────── */
    public void setCustomId(String id)       { this.customId = id; }

    /* ── death context ───────────────────────── */
    public DeathContext getDeathContext()     { return deathContext; }
    public void setDeathContext(DeathContext c) { this.deathContext = c; }

    /** Consume and clear the death context (called on respawn). */
    public DeathContext consumeDeathContext() {
        DeathContext c = deathContext;
        deathContext = null;
        return c;
    }
}
