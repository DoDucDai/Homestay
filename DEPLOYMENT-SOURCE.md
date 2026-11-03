# Demo deployment source

Build exclusively with `Dockerfile.render`. It unpacks the existing base `deployment-source.tar.gz` then overlays `deployment-patch.tar.gz` before compilation. The overlay contains all current text source files; unchanged image/font assets remain in the base. Other unpacked repository directories may be older.

Base archive SHA-256: `0b40e7d9d0c378df3f5a30c3a62eedd5edd67c1ee6821f88d68610a8b4a730c9` (commit 8819698).

Overlay SHA-256: `9404549fd88e7c3f7ba5afbe336cb9d86ac1e93742a0d12de97875f29366a87d`.

The final file checksums are in `deployment-manifest.json`. No environment files, SQL exports, SQLite databases or customer records are included.
