# sync — build & publish

Scala 3 multi-module library (`a8-*` artifacts) consumed by other a8 repos (checkpoint, godev, …)
via a pinned version string. Modules: `shared` (`a8-sync-shared`), `nats` (`a8-nats`),
`hermesProto` (`a8-hermes-proto`), `hermes` (`a8-hermes`), `loggingJVM`/`logging_logback`,
`schedulerDsl`. The RPC framework lives in `hermes/src/main/scala/a8/hermes/rpc/`.

## Environment
Nix + direnv. Enter the shell first: `direnv allow` (or prefix commands with `nix develop --command`).
Do not install toolchains globally.

## Build / test
```
nix develop --command sbt -batch -no-colors "hermes/compile"   # one module
nix develop --command sbt -batch -no-colors test               # all
```

## Publish (READ THIS — the version-stamp trap)
The version is a **timestamp minted fresh on every sbt invocation** (`a8.sbt_a8.versionStamp`, see the
`using version = 1.0.0-<stamp>_master` line at startup). Therefore:

- **Publish the whole repo in ONE invocation** so every artifact shares one version:
  ```
  nix develop --command sbt -batch -no-colors clean publish
  ```
  Root `clean publish` aggregates all modules. Grab the `using version = …` it prints.
- **Do NOT** publish modules in separate `sbt` calls — each call re-stamps, so the artifacts land at
  different versions and a consumer pinning one version fails to resolve the rest.
- **Do NOT** add `set every publishConfiguration ~= (_.withOverwrite(true))` — it triggers a
  `Cyclic reference … publishConfiguration` error. A fresh version never collides, so overwrite is
  unneeded; if you truly must overwrite, drop a single stale artifact instead.

Targets the a8 repo (locus2) via `publishTo` + `~/.sbt` credentials.

## Consuming a change downstream (e.g. checkpoint)
Consumers pin ONE version for all sync artifacts (checkpoint: `val syncSharedVersion` in `build.sbt`).
After publishing sync, bump that string to the new stamp and rebuild/redeploy the consumer:
```
# in sync:        sbt clean publish        -> note 1.0.0-<stamp>_master
# in consumer:    set syncSharedVersion = "1.0.0-<stamp>_master"; then  sbt clean publish
```

## Notes
- `scalapb-json4s` (in `hermes`) backs protojson — `TypedRpcHandler` decodes/encodes by the request's
  `ContentType` (a `Json` request is protojson; otherwise binary protobuf), so one typed handler serves
  both browser (JSON ws) and protobuf callers.
- The RPC server replies to EVERY request — a throwing handler returns an `ErrorResponse`, never a hang.
