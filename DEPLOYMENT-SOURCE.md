# Demo deployment source

Build exclusively with `Dockerfile.render`. It unpacks the existing base `deployment-source.tar.gz` then overlays `deployment-patch.tar.gz` before compilation. The overlay contains all current text source files; unchanged image/font assets remain in the base. Other unpacked repository directories may be older.

Base archive SHA-256: `0b40e7d9d0c378df3f5a30c3a62eedd5edd67c1ee6821f88d68610a8b4a730c9` (commit 8819698).

Overlay SHA-256: `ebd2dd31f74fe95131a1b45592fa57724ffe087acf2c22a86fd3e0477b75495e`.

The final file checksums are in `deployment-manifest.json`. No environment files, SQL exports, SQLite databases or customer records are included.
