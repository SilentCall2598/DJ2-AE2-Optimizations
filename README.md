<h1 align="center">DJ2 AE2 Optimizations</h1>

<p align="center">
  <strong>Targeted AE2 performance patches for large Divine Journey 2 storage networks.</strong>
</p>

<p align="center">
  <a href="https://github.com/SilentCall2598/DJ2-AE2-Optimizations/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/SilentCall2598/DJ2-AE2-Optimizations?label=release"></a>
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-1.12.2-62B47A">
  <img alt="Forge" src="https://img.shields.io/badge/Forge-14.23.5.2860-E04E14">
  <img alt="Java" src="https://img.shields.io/badge/runtime-Java%208-007396">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-MIT-blue"></a>
</p>

<p align="center">
  <a href="https://github.com/SilentCall2598/DJ2-AE2-Optimizations/releases/latest"><strong>Download the latest release</strong></a>
</p>

## Overview

DJ2 AE2 Optimizations is a performance mod built specifically for the Applied Energistics 2 setup used by **Divine Journey 2** on Minecraft 1.12.2.

Large late-game networks can spend a noticeable amount of server-thread time polling Storage Drawers, scanning inventories, routing Interface transfers, and refreshing storage state. This mod targets those measured hot paths without replacing AE2 or changing vanilla classes.

The project is intentionally conservative. An optimized path is used only when the required state can be proven safe. If a version, inventory, index, or runtime assumption cannot be verified, the affected path falls back to the original behavior.

## Highlights

### Storage Drawers polling

- Reuses AE2 item conversions for stable drawer prototypes instead of repeating the same registry work every poll.
- Uses a cheaper value-based diff once prototype identity has been confirmed stable.
- Includes an optional steady-state mode that reconciles the live AE2 cache in place instead of rebuilding a fresh item list every poll.
- Prunes stale conversion templates so long-running or heavily changed drawer networks remain bounded.

### Drawer extraction

- Maintains a conservative presence index for controller-backed drawer networks.
- Immediately answers requests that are provably absent instead of scanning every drawer slot.
- Narrows key-present fallback scans to candidate slots while preserving the original slot order and stock extraction behavior.
- Supports normal, compacting, fractional, and ore-dictionary-aware drawer cases covered by the audited Storage Drawers paths.

### External inventories

An optional negative-extraction fast path is available for the exact integrations that were audited and tested:

- Ender Utilities JSU
- Actually Additions Large Storage Crate

Other `IItemHandler` implementations continue through the original AE2 path.

### AE2 Interface routing

An optional per-operation routing context can reuse a proven-negative SIMULATE result during the paired MODULATE pass. Positive handlers still run their real MODULATE call, and extraction and insertion evidence are kept separate.

### Thaumic Energistics

An optional incremental update path replaces unnecessary broad storage refreshes with precise essentia deltas when the connected container has not changed. Topology changes and uncertain states fall back to the original broad update.

### Registry remapping

Forge item ID remaps invalidate the ore-key cache and related presence state so cached keys cannot carry across a changed registry mapping.

## Compatibility

Version 1.0.0 is built and tested against the Divine Journey 2 environment below.

| Component | Version |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2860 |
| Applied Energistics 2 UEL | 0.56.4 |
| Storage Drawers | 5.5.0 |
| MixinBooter | 10.7 |
| Ender Utilities | 0.7.15 |
| Actually Additions | 1.12.2-r152 |
| Thaumic Energistics Extended Life | 2.2.7 jar, runtime version 2.2.6 |

The Ender Utilities, Actually Additions, and Thaumic Energistics patches are optional integrations. If an integration mod is absent or does not match the validated version, its mixins are not used.

This is a DJ2-specific optimization mod, not a general-purpose AE2 compatibility layer. Exact version checks exist because several optimizations target implementation details that are not stable public APIs.

## Installation

1. Install or open your Divine Journey 2 instance.
2. Download `dj2-ae2-optimizations-1.0.0.jar` from the Releases page.
3. Place the jar in the instance's `mods` folder.
4. Start the game normally.

Do not use the `-dev.jar` as the normal release artifact.

## Configuration

The config file is created at:

```text
config/dj2ae2opt.cfg
```

The file is rewritten in full on startup, so edit values rather than relying on custom formatting or comments inside the generated file.

### Production options

| Setting | Default | Purpose |
|---|:---:|---|
| `optimizeDrawerInventoryPolling` | `true` | Storage Drawers conversion cache |
| `optimizeDrawerInventoryDiff` | `true` | Cheaper diffing for confirmed-stable drawer inventories |
| `optimizeDrawerNegativeExtraction` | `true` | Proven-absent drawer extraction fast path |
| `optimizeDrawerCandidateNarrowing` | `true` | Narrows key-present fallback scans to candidate drawer slots |
| `optimizeExternalItemHandlerNegativeExtraction` | `false` | Optional JSU and Large Storage Crate negative-extraction fast path |
| `optimizeThaumicEnergisticsIncrementalUpdate` | `false` | Optional precise essentia storage updates |
| `optimizeDrawerSteadyStatePolling` | `false` | Optional in-place reconciliation for stable drawer inventories |
| `optimizeInterfaceTransferRouting` | `false` | Optional reuse of proven-negative Interface routing results |
| `allowUnverifiedModVersions` | `false` | Allows unverified dependency versions when explicitly enabled |

The defaults are intentionally conservative. Diagnostic instrumentation is also disabled by default.

## Status and diagnostics

The mod includes an in-game status dashboard:

```text
/dj2ae2opt status
```

For more detail:

```text
/dj2ae2opt status full
/dj2ae2opt status mixins
/dj2ae2opt status counters
```

The compact status view shows feature state, compatibility, important production ratios, and health warnings without requiring a log search.

## Validation

The 1.0.0 release went through both isolated regression testing and live DJ2 validation, including:

- 16 standalone regression and equivalence harnesses
- Real-target verification of 25 mixins and 50 annotation references
- Negative verifier fixtures that are required to fail for the expected reasons
- Extended live testing against the target mod versions
- A 600-second production-workload Spark profile
- Live steady-state and cache-pruning validation under drawer topology and content churn
- Deterministic Forge item-ID remapping regression coverage
- Release-jar content auditing
- Two independent clean builds producing a byte-identical release jar

The final live tests kept the optimized drawer paths healthy with no steady-state invariant failures.

## Release integrity

Release artifact:

```text
dj2-ae2-optimizations-1.0.0.jar
```

SHA-256:

```text
7de5787360fe9dd7d676781cac71e31c71b9abd37ff1f820638ec6faea8c9df4
```

The release jar contains this mod's classes and resources only. The pinned third-party build dependencies are not bundled into it.

## Building from source

The Gradle wrapper is included. The build runs with JDK 25 and produces Java 8-compatible bytecode.

```text
./gradlew clean build
```

The full release verification suite can be run with:

```text
./gradlew releaseCheck -Ppython=<python-interpreter>
```

Build output is written to:

```text
build/libs/
```

The repository keeps the exact compile-time dependency jars used by the verifier under `libs/` so the target bytecode is fixed and reproducible.

## License

DJ2 AE2 Optimizations is licensed under the MIT License.

The pinned third-party projects retain their own licenses. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and `third_party_licenses/` for the corresponding notices and license texts.
