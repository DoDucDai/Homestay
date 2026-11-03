# Demo deployment source

Build exclusively with `Dockerfile.render`. It unpacks the existing base `deployment-source.tar.gz` then overlays `deployment-patch.tar.gz` before compilation. The overlay contains all current text source files; unchanged image/font assets remain in the base. Other unpacked repository directories may be older.

Base archive SHA-256: `0b40e7d9d0c378df3f5a30c3a62eedd5edd67c1ee6821f88d68610a8b4a730c9` (commit 8819698).

Overlay SHA-256: `997a377af8b44ba1e9e477036b6f4808d80ab854458e6364aeb0c8a9d028e7e0`.

The final file checksums are in `deployment-manifest.json`. No environment files, SQL exports, SQLite databases or customer records are included.

Room expansion: `room-assets-expansion.tar.gz` adds 74 reviewed WebP posters after the source overlay. Asset archive SHA-256: `2643680462024faa721843f0fc95f047f63cab603c3750ec232b6d851a7b4472`. Duplicate source numbers 408–413 are excluded.
