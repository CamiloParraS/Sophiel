---
name: Sophiel
description: On-device visual safety layer; a dark tiled state card over a bright working page, in Esmeralda and Geist (D75).
colors:
  moss: "#08201A"
  on-moss: "#F0F7F3"
  text-0: "#F0F7F3"
  text-1: "#A9C6BA"
  mint: "#3FD08F"
  mint-dim: "#3A9C6E"
  hero-off: "#12281F"
  tile-on: "#0F3226"
  hero-edge: "#BFD8A8"
  hero-red: "#2A1517"
  hero-amber: "#2A2115"
  green: "#3FD08F"
  esmeralda: "#00804A"
  orange: "#E66100"
  orange-fg: "#B34A00"
  red: "#E01B24"
  red-fg: "#C01C28"
  blue: "#3584E4"
  ink: "#1C1E21"
  dim: "#1C1E21BD"
  border: "#1C1E211A"
  btn: "#1C1E2112"
  chalk: "#F1F2F1"
  surface: "#FFFFFF"
typography:
  display-large:
    fontFamily: "Geist"
    fontSize: "56sp"
    fontWeight: 700
    lineHeight: "56sp"
  display-medium:
    fontFamily: "Geist"
    fontSize: "46sp"
    fontWeight: 700
    lineHeight: "46sp"
  display-small:
    fontFamily: "Geist"
    fontSize: "40sp"
    fontWeight: 700
    lineHeight: "42sp"
  headline:
    fontFamily: "Geist"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: "32sp"
  title-large:
    fontFamily: "Geist"
    fontSize: "21sp"
    fontWeight: 600
    lineHeight: "26sp"
  title:
    fontFamily: "Geist"
    fontSize: "17sp"
    fontWeight: 600
    lineHeight: "22sp"
  body:
    fontFamily: "Geist"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "23sp"
  body-medium:
    fontFamily: "Geist"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
  label:
    fontFamily: "Geist"
    fontSize: "15sp"
    fontWeight: 600
    lineHeight: "21sp"
  button-big:
    fontFamily: "Geist"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "22sp"
rounded:
  fix: "8dp"
  icon: "10dp"
  button: "12dp"
  group: "16dp"
  tile: "24dp"
  pill: "999dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  row-x: "14dp"
  lg: "16dp"
  xl: "24dp"
components:
  button-go:
    backgroundColor: "{colors.moss}"
    textColor: "{colors.on-moss}"
    typography: "{typography.button-big}"
    rounded: "{rounded.button}"
    height: "56dp"
  button-default:
    backgroundColor: "{colors.btn}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.button}"
    height: "40dp"
    padding: "0 20dp"
  button-fix:
    backgroundColor: "#E6610026"
    textColor: "{colors.orange-fg}"
    rounded: "{rounded.fix}"
    height: "40dp"
    padding: "0 14dp"
  preference-group:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.group}"
  bento-tile:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.tile}"
  pref-row:
    textColor: "{colors.ink}"
    typography: "{typography.title}"
    height: "56dp"
    padding: "8dp 14dp"
  tag-ok:
    backgroundColor: "#3FD08F33"
    textColor: "{colors.esmeralda}"
    rounded: "{rounded.pill}"
    padding: "2dp 10dp"
  segmented:
    backgroundColor: "{colors.btn}"
    textColor: "{colors.dim}"
    rounded: "{rounded.pill}"
    height: "48dp"
---

# Design System: Sophiel

## Overview

**Creative North Star: "The Tiled Greenhouse"**

Sophiel is a glasshouse at night. The state card at the top of Status is moss-dark glass divided into square panes: the tile wall that is literally how the app sees the screen. When something is covered, a pane fogs over with the mask's camo. Below the glass sits the bright potting bench: a cool chalk page where the Parent does the actual work.

The feel is warm, protective, tactile. The padlock shuts with a small spring, the switch thumb slides, buttons give at .97 when pressed. Personality lives in the state card, the cookie badge and the tiles; the bench below stays plain so settings read at a glance. One dark ink (Moss) does every primary job; Esmeralda, amber and red appear only to state the truth about Protection.

Light theme only (D43). No Material You dynamic colour (D65, D70): the brand ink is fixed.

**Key Characteristics:**
- Two heroes carry the tile wall: the Status state card and the Historial header. Every other screen has a light header: a white 44dp back disc and a bold title on the chalk page (D75).
- Status is a bento: the state card, then Historial (Esmeralda), Apps and Revisión as white 24dp tiles.
- Geist throughout, weights 400-700, sp sizes.
- State colour is a promise: Esmeralda on, amber stopped, red broken. Never decoration.
- The Child's screens (the block screen, the masks) live in the same tile world, on Moss.
- Tactile micro-motion with one expressive curve; all of it snaps under Remove animations.

## Colors

One near-black green ink, fixed state colours, and cool neutrals that never reach black.

### Primary
- **Greenhouse Moss** (moss): the state card's ink, the 56dp Start button, the one primary everywhere. On it sit **Glass Text** (text-0) and **Lichen** (text-1) for secondary text.
- **Sprout Mint** (mint, same as green): the lit state on the dark hero; **Held Mint** (mint-dim) is the same green held back for Ready.

### Secondary (state)
- **Esmeralda** (esmeralda #00804A): Protection on as text and fill on light; 5.0:1 with white. **Green** (#3FD08F) is its form on dark.
- **Stopped Amber** (orange, text form orange-fg): stopped Protection, Fix buttons, amber hero page (hero-amber).
- **Broken Red** (red, text form red-fg): Sin protección, destructive actions, red hero page (hero-red). Shown honestly, never softened.

### Tertiary
- **Low Band Blue** (blue): the Log's LOW band only.

### Neutral
- **Bench Ink** (ink): all text on light; never pure black. **Dim** is ink at 74% (~6.5:1 on chalk).
- **Chalk** (chalk #F1F2F1): the page behind everything light.
- **Pane White** (surface): cards, groups, tiles, sheets, dialogs, the selected segment.
- **Hairline** (border, ink 10%), **Button Wash** (btn, ink 7%), hover at ink 6%.

### Named Rules
**The Truth Colour Rule.** Esmeralda, amber and red describe Protection's real state and nothing else. A decorative green is a lie on this screen. Data bars (the per-app counts) are the one exception: they wear Esmeralda at low alpha because they count masked things.
**The One Ink Rule.** Moss is the only primary. Every filled main action is Moss, never a state colour.

## Typography

**Display Font:** Geist (bundled, D75)
**Body Font:** Geist
**Label/Mono Font:** Geist (tabular figures for times and counts)

**Character:** Geist's tight, modern grotesk makes the big count feel confident and measured; Bold (700) carries the display sizes and SemiBold (600) every title and button.

### Hierarchy
- **Display** (700, 56 / 46 / 40sp, tight leading): the Log's masked count, Status's count, the hero title. Headlines cap their font scale so one word still fits.
- **Headline** (600, 28sp / 32sp): status page titles.
- **Title** (600, 21sp sheets and alerts; 17sp header and row titles).
- **Body** (400, 16sp / 14sp row descriptions / 12-13sp small).
- **Label** (600, 15sp buttons; 14sp group titles in Dim; 16sp for 56dp main buttons).

### Named Rules
**The Big Number Rule.** A count is the headline. Numbers get the display size; their caption sits small beside or below.
**The One Word Rule.** The Spanish for "masked" is "tapado" everywhere (Tapados, 4 tapados, zona tapada). Never "cubiertas" or "cubiertos".

## Layout

Single column. Content is centred at a 640dp reading width on tablets and landscape, backgrounds full-bleed. The page gutter is 14-16dp; group titles sit 4dp inside the card edge. Rows are 56dp minimum, 14dp horizontal padding, 12dp between icon, text and end slot. Every target is 48dp to hit regardless of what it draws. Edge-to-edge with status-bar and side-cutout insets. Status icons are dark on the page and light only while a dark header shows (one owner, `StatusIconsHost`).

## Elevation & Depth

Mostly flat with tonal steps: chalk page, white panes lifted by a faint 2dp ink shadow (18% ink ambient and spot). The heroes have no shadow; depth comes from the tile grid, the cookie badge's halo ring and the pane edges. The segmented thumb and switch thumb take a 1-2dp shadow to read as physical.

### Named Rules
**The Faint Lift Rule.** White on chalk plus a 2dp ink shadow is the only card elevation. No heavier shadows, no coloured shadows.

## Shapes

Gently rounded rectangles. Every text button and main action is 12dp (D74: one button shape; pills read as another app). Fix buttons 8dp, icon buttons 10dp, preference groups 16dp, bento tiles and the state card 24dp. Pills survive only for tags and the segmented control. The hero is a strict square grid; the badge is a cookie (10 lobes), and an app's icon on the block screen is clipped to it.

## Components

### Buttons
- **Shape:** gently curved (12dp), one shape for all text buttons.
- **Go (main):** Moss fill, on-moss text, 56dp, ButtonBig label. **Stop:** outlined, Moss ink on white, with the lock glyph while locked.
- **Default:** ink 7% wash, 40dp, 20dp sides. **Destructive:** red 12% tint, red-fg text. **Fix:** amber 15% tint, 55% amber-fg outline, 8dp corners.
- **States:** press scales to .97 over 100-120ms on the Ease curve; disabled drops to 70% legible with its reason line; busy swaps the glyph for a spinner and ignores taps; keyboard focus draws a 2dp Esmeralda ring.

### Chips / Tags
- **Style:** pill, 2dp x 10dp, body-small. State tag colour at 12-20% alpha with the matching text.

### Cards / Containers (Preference Group, Bento Tile)
- **Preference group:** 16dp, Pane White, Faint Lift, 1dp hairline dividers between rows only. Small Dim title above, optional Dim footnote below.
- **Bento tile (Status):** 24dp, white (or Esmeralda for Historial), a title with a chevron on top.

### Inputs / Controls
- **Round switch:** 46 x 26, Moss track when on, ink 15% off, white thumb sliding 200ms. Rows toggle from anywhere.
- **Segmented control:** ink-wash pill track, white sliding thumb (220ms), stacks into a rounded list when a label cannot fit its third. Pinned in Historial on an opaque chalk band taller than the pill.

### Navigation
- **Header (light):** a white 44dp back disc, a bold 17sp title in ink, the unlocked ring chip at right on Parent screens. Dark icons.
- **Header (dark, Historial):** transparent over the hero, then Moss once the sheet reaches it. Light icons.

### Grid Hero (signature)
The tile wall: square panes in hero-off / tile-on with hero-edge outlines on Moss; covered tiles wear the camo mask; a 78dp cookie badge with the launcher padlock that springs shut; a cold-open welcome wave plays once per process. Page tint shifts to hero-red or hero-amber when Protection is broken or stopped. The block screen reuses the still wall.

## Do's and Don'ts

### Do:
- **Do** use Moss for every primary action; keep state colours for state.
- **Do** show Sin protección in red, plainly.
- **Do** keep every touch target 48dp and every size in sp.
- **Do** route all motion through `motion()` so Remove animations snaps it.
- **Do** show the shape of a list (skeleton rows) while it loads; never an empty page.
- **Do** tell apart rows that share a name (the package under the label).

### Don't:
- **Don't** follow the system dark mode or wallpaper colour (D43, D65).
- **Don't** use pill-shaped text buttons (D74).
- **Don't** use pure black text or heavier card shadows.
- **Don't** add a second accent colour for decoration.
- **Don't** set status-bar icon colour from a screen; add `LightStatusIcons()` to a dark header and let the host decide.
