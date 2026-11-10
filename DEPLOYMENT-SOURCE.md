# Deployment source

Build with `Dockerfile.render`, which is identical to `Dockerfile` and compiles the checked-in Java 25 source directly.
The Render branch and configured Dockerfile path are unchanged.

The source snapshot includes the authorized working tree from `ai-rag` (base commit `dcf31e3`) on 2026-11-10.
Per-file SHA-256 values are in `deployment-manifest.json`. All 74 existing room posters were compared byte-for-byte.

Historical `deployment-source.tar.gz`, `deployment-patch.tar.gz` and `room-assets-expansion.tar.gz` are retained for history only.
They are excluded by `.dockerignore` and are not read by the current Dockerfile. No environment files, database backups,
SQLite files or customer contribution records were added to this snapshot.

Local email checks passed, including real HTTP/SQL workflows with captured mail on an isolated database.
See `KIEM_THU_EMAIL.md`. The completed work plans were removed; test evidence is retained.

Production email still requires the service environment variables documented in `EMAIL_SETUP.md`.
A successful Git push does not by itself prove a Render deployment is live or that email variables are configured.
