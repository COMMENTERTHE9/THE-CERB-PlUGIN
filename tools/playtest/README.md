# Playtest smoke test

Headless in-game checks for Cerberus, for a **local sandbox server only** (it joins with
`online-mode=false`). Used by cloud/Linux sessions to verify gameplay after a change.

```
cd tools/playtest && npm install          # once
# start a sandbox server (tools/cerb.sh run with online-mode=false), then in its console:  op CerbSmoke
node smoke.js                             # PASS/FAIL per check, non-zero exit on failure
```

Never point it at a server with real players or online-mode=true.
