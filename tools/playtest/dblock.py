#!/usr/bin/env python3
"""Sandbox test tool for tools/playtest/persist.js (never point it at a live server's database).

lock <db> <seconds>   hold SQLite's write lock (BEGIN IMMEDIATE) for that long, as another program with the file open in a
                      transaction would (a database browser, a backup tool): the plugin's writes wait, or fail after the
                      driver's busy timeout. Prints LOCKED when held and RELEASED when let go.
xlock <db> <seconds>  the same with BEGIN EXCLUSIVE: reads wait too, so a player's load is delayed (or fails).
read <db> <player> <skill>   that player's stored level and XP for a skill, read-only: [level, xp] or null.
seq <db> <player>     the highest saved_seq of that player's rows (null when the column doesn't exist yet).
"""
import json
import sqlite3
import sys
import time


def lock(db, seconds, mode='IMMEDIATE'):
    con = sqlite3.connect(db, timeout=30, isolation_level=None)
    con.execute('BEGIN ' + mode)
    print('LOCKED', flush=True)
    time.sleep(seconds)
    con.execute('ROLLBACK')
    con.close()
    print('RELEASED', flush=True)


def ro(db):
    return sqlite3.connect('file:' + db + '?mode=ro', uri=True, timeout=30)


def read(db, player, skill):
    con = ro(db)
    row = con.execute(
        'SELECT s.skill_level, s.skill_xp FROM player_skills s JOIN player_custom_ids c ON c.custom_id = s.custom_id '
        'WHERE c.player_name = ? AND s.skill_name = ?', (player, skill)).fetchone()
    con.close()
    print(json.dumps(list(row) if row else None))


def seq(db, player):
    con = ro(db)
    cols = [r[1] for r in con.execute('PRAGMA table_info(player_skills)')]
    if 'saved_seq' not in cols:
        print('null')
        return
    row = con.execute(
        'SELECT MAX(s.saved_seq) FROM player_skills s JOIN player_custom_ids c ON c.custom_id = s.custom_id '
        'WHERE c.player_name = ?', (player,)).fetchone()
    con.close()
    print(json.dumps(row[0] if row else None))


if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'lock':
        lock(sys.argv[2], float(sys.argv[3]))
    elif cmd == 'xlock':  # EXCLUSIVE: reads wait too (a player's load is delayed, or fails after the busy timeout)
        lock(sys.argv[2], float(sys.argv[3]), 'EXCLUSIVE')
    elif cmd == 'read':
        read(sys.argv[2], sys.argv[3], sys.argv[4])
    elif cmd == 'seq':
        seq(sys.argv[2], sys.argv[3])
    else:
        sys.exit('unknown command ' + cmd)
