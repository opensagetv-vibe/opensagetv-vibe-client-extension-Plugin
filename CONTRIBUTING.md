# Contributing

Keep this plugin compatible with an unmodified stock SageTV server. Changes to
DVD behavior require a reproducible failure, a focused unit/source gate, and
physical playback evidence on the affected Android device before release.

Run `dev.cmd all` on Windows or `./dev.sh all` on Linux/WSL. The build uses a
stock `Sage.jar` only as a private compile dependency; do not commit or package
that JAR. Keep FFmpeg/MIM execution in the separate Vibe FFmpeg plugin.

Update `TASKS.md`, `CHANGELOG.md`, and `HANDOFF.md` with the relevant evidence.
Before a commit, move completed checkboxes to `## Checklist change ledger` and
review the workspace `task.md` suggested execution order.
