# Development Guide

## First-time setup
1. Clone the repo
2. Run `setup.sh` (or `setup.bat` on Windows) — downloads Paper 1.21.1 server JAR
3. Run `run.sh` (or `run.bat`) — builds plugin, deploys, starts server
4. Join `localhost:25565`

## Daily workflow
- `run.sh` — build + deploy + start server (main dev loop)
- `deploy.sh` — build + copy JAR only; restart server after to load changes
- Do NOT rely on `/reload confirm` — it can hide lifecycle bugs, leak memory,
  and duplicate listeners. Always prefer a clean server restart.

## If something goes wrong
- Server logs: `test-server/logs/latest.log`
- Plugin JAR: `test-server/plugins/Cerberus.jar`
- Heap dumps (OOM): `test-server/` (files named `java_pid*.hprof`)
- Build output: `target/Cerberus.jar`

## JVM memory
Default: 512MB min, 2GB max. Override with environment variables:
```
JAVA_XMS=1G JAVA_XMX=4G ./run.sh
```

## Commands to test
- `/cerb status` — plugin status
- `/spawncustomzombie 100` — spawn test mob
- `/givetestitem` — get test items
- `/givetestarmor` — get test armor

## Stopping the server
Type `stop` in the server console. Do not kill -9 unless the server is hung.
