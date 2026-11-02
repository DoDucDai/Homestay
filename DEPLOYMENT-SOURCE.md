# Demo deployment source

This branch packages the current local Homestay source for a browser-upload deployment. The unpacked files in other repository directories can be older; Render must use `Dockerfile.render`, which builds exclusively from `deployment-source.tar.gz`.

The archive contains readable source files (not a compiled application). See `deployment-manifest.json` for paths and SHA-256 checksums. No environment files, SQL exports, SQLite databases, or customer records are included. Only the reviewed public RAG knowledge bundle is included.

Archive SHA-256: `93e34ac57143bd1e19d276c0fe57b2033f85bb7adefaca4fab7184a2b103328e`

Build locally: `docker build -f Dockerfile.render .`

Render: Docker runtime, free instance, health check `/api/health`; set database and model credentials as environment secrets. Persistent RAG edits and support data are unavailable on the free ephemeral filesystem.
