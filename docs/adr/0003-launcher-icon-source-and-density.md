# 0003 — Launcher icon: adaptive at one density, pre-scaled foreground

## Status

Accepted, 2026-05-22.

## Context

M0 shipped a placeholder launcher icon: two simple vector paths on
a solid `#1F6FEB` background. The v0.4.x branding pass swapped it
for the Asala brand mark (cream calendar over a teal gradient).

The brand source files live in `logos/` (gitignored; private brand
assets), and include:

- `asala-adaptive-foreground-432.png` — calendar shape with the
  brand "12" on a transparent background.
- `asala-adaptive-background-432.png` — radial teal gradient
  (`#1F4A56` to `#0C2A33` approximately).
- `asala-master-1024.png` / `.svg` — Play Store / web logo.
- `asala-master-1024-day{1..31}.svg` — 31 day-of-month variants
  intended for the dynamic launcher-icon feature on the ROADMAP.
- `asala-playstore-512.png` — Play Store listing icon.

The brand foreground occupies roughly 80% of the 432 × 432 canvas
(calendar shape's corners at ~178 px from center). Pixel Launcher's
adaptive-icon mask shows the central 72 dp of the 108 dp canvas
(≈ 290 px square in a 432 × 432 source), so corners of an
unscaled foreground extend past the visible area and get cropped.

Resource shrinking is on per
[commit `9e88f62`](../../../commit/9e88f62), so unused density
buckets do not bloat the APK.

## Decision

### 1. Adaptive icon, not legacy

Keep `mipmap-anydpi-v26/ic_launcher{,_round}.xml` as the
single launcher icon path. Adaptive icons get parallax / zoom
animations on Pixel Launcher and themed-icon support; legacy PNGs
do not.

### 2. One density: `drawable-xxxhdpi`

Ship `ic_launcher_foreground.png` and `ic_launcher_background.png`
at 432 × 432 in `drawable-xxxhdpi` only. Skip mdpi / hdpi / xhdpi /
xxhdpi buckets. Android downscales at runtime on lower-density
devices; resource shrinking prevents the unused buckets from
bloating anything anyway.

Trade-off: marginal quality loss when downscaled on a 280-ppi
phone (rare on Android 14+). Both layers are flat colour blocks
plus geometric shapes, so the resampling artefacts are invisible
in practice.

### 3. Foreground pre-scaled to 75% of the canvas

The in-tree foreground PNG is generated from the brand source by
LANCZOS-resampling the 432 × 432 source to 324 × 324 and pasting
it centred on a 432 × 432 transparent canvas. Generator script is
`/tmp/icon_finalize.py` (transient; not committed). The calendar
shape's geometric centre lands at (216, 216) — exactly the canvas
centre.

Why 75%: verified empirically against Pixel Launcher and Pro
Launcher on a Pixel 10 Pro XL. Larger scales (82 - 100%) crop
the calendar corners under Pixel Launcher's mask; 70% and below
feel small relative to the visible icon area.

This is reversible — the brand source is unchanged. If a
different launcher's mask requires a different scale later,
regenerate with `python icon_finalize.py <scale>`.

### 4. Day-N variants stay in `logos/`

The 31 day-of-month SVGs (`asala-master-1024-day{1..31}.svg`)
are design source for the dynamic launcher-icon milestone listed
under "Visual polish → Dynamic launcher icon that shows today's
day-of-month number" in `docs/ROADMAP.md`. Wiring them is a full
milestone: each variant needs its own adaptive icon, plus an
`<activity-alias>` per day, plus a `WorkManager` job that flips
the enabled alias at midnight. Out of scope for v0.4.x.

### 5. Play Store listing icon deferred

`asala-playstore-512.png` is the Play Store listing image. It is
not copied into `res/`. Add it under
`res/mipmap-xxxhdpi/ic_launcher_playstore.png` only if and when
we publish to Play.

## Consequences

- The in-tree icon assets are derived. The source of truth lives
  in `logos/` (gitignored). Regenerate the foreground with
  `python /tmp/icon_finalize.py <scale>` against the source PNG.
  If the source changes, regenerate.
- The dynamic launcher-icon feature has a clean place to land:
  add `mipmap-anydpi-v26/ic_launcher_day{N}.xml` references and
  the corresponding `drawable-xxxhdpi/ic_launcher_foreground_day{N}.png`
  derived from `logos/asala-master-1024-day{N}.svg`.
- Themed-icon (Android 13+ monochrome variant) is not wired. If
  we want it, add a `<monochrome>` layer to the adaptive-icon
  XML and ship a third PNG. Defer until requested.
- A reviewer who wants to verify the icon scaling: the source
  PNG's calendar-shape bbox is (95, 85, 337, 347); after 75%
  resampling and centred paste, the resulting bbox is
  (125, 118, 307, 314), centred at (216, 216).

## Update — 2026-06-19 (v0.22.0)

The icon was redesigned for the Fadetouched palette (dark teal-green
background, warm rust header, dark numeral). Decisions 1 (adaptive
icon) and 2 (single `drawable-xxxhdpi` density at 432 × 432) still
hold unchanged. What changed:

- **Source of truth is now Affinity Designer.** `logos/asala.af` is
  the editable master; `logos/ic_launcher_foreground.svg` and
  `logos/ic_launcher_background.svg` are its vector exports. The
  `asala-adaptive-*` PNGs and the `/tmp/icon_finalize.py` 75 %
  rescale pipeline in decision 3 are retired: the foreground is now
  composed in Affinity to sit inside the 66 dp safe zone directly,
  so no post-export rescale step is needed.
- The background is a near-flat dark teal (`#11201E`) with a soft
  gradient, replacing the old `#1F4A56 → #0C2A33` radial.
- Still no `<monochrome>` themed-icon layer (Android 13+ themed
  icons remain unsupported); adding one is the natural next step.

`logos/` remains gitignored, so the in-tree `res/` PNGs stay the
only committed icon assets. The brand image
(`docs/brand/asala-icon.png`) is regenerated by compositing the two
`res/` layers.
