# Development Guide

The build / deploy / start / join / test / stop workflow lives in **[RESTART_GUIDE.md](RESTART_GUIDE.md)**.

Short version (Windows): `status.bat` to check your setup, `run.bat` to build + deploy + start,
type `stop` in the server window to stop. Linux/macOS: `./run.sh`, `tools/cerb.sh status`.

Rules that still apply:
- Never use `/reload` — always stop and restart the server (reload duplicates listeners/tasks).
- Run the unit tests before pushing: `mvnw.cmd test` (Windows) or `./mvnw test`.
