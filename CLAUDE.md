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

## `@CompanionGen` / `Mx*.scala` files (code generation)

Case classes annotated `@CompanionGen` (from `a8.shared`) have a **checked-in generated companion**
in a sibling `Mx<Name>.scala` file (marked "DO NOT EDIT"). It holds the JSON codec, `parameters`,
and `unsafe.{raw,iter,typed}Construct` — all POSITIONAL, so it breaks the moment you add/remove/reorder
a field. Do NOT hand-edit these files; regenerate them.

**Regenerate with `a8-codegen`** (on PATH in the nix dev shell — `nix develop`):
- It scans for `@CompanionGen` scala files **RECURSIVELY from the current directory** and rewrites
  each one's `Mx*.scala`. Its output is just `generated <path>` lines then `SUCCESS`.
- **Run it from the SMALLEST dir covering your changed files** (e.g. the module or package dir), NOT
  the repo root — from the root it regenerates EVERY `@CompanionGen` in the repo, and unrelated files
  in other modules can regenerate into a broken state (seen 2026-07-25: a `shared/` Mx file emitted
  "missing outer accessor"). If you must run wide, `git checkout master -- <collateral>` the files
  outside your change.
- Multiple `@CompanionGen` classes in ONE package share ONE `Mx<package-or-firstclass>.scala` (each
  gets its own `trait Mx<ClassName>` inside).

**Wiring the generated trait in:** the case class's companion `object` must `extends Mx<ClassName>`
to pick up the implicit `jsonCodec` etc. (see `object HermesAppConfig extends MxHermesAppConfig`). If a
`@CompanionGen` case class is used as a FIELD of another `@CompanionGen` case class, the field type's
companion MUST extend its Mx trait or the outer codec fails to compile ("No given instance of type
JsonCodec[...]"). A companion that only needs `load()`/helpers can still skip the mixin IF nothing
forces its codec — but a nested-field codec does force it.
