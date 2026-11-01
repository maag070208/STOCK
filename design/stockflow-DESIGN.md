---
version: "beta"
name: "StockFlow Bento"
description: "Light bento workspace: soft gray canvas, borderless white cards with 24px corners, pastel tiles and a bold orange accent. Density in tables, air on dashboards."
colors:
  background: "#f4f4f6"
  on-background: "#14171c"
  surface: "#ffffff"
  surface-soft: "#eef0f3"
  surface-elevated: "#ffffff"
  on-surface: "#1f2937"
  on-surface-muted: "#64748b"
  outline: "#e5e7eb"
  outline-strong: "#d1d5db"
  primary: "#fe6e00"
  primary-strong: "#ea580c"
  primary-warm: "#ffb74d"
  primary-focus: "#f97015"
  on-primary: "#ffffff"
  shell-base: "#ffffff"
  on-shell: "#14171c"
  shell-border: "#e5e7eb"
  success: "#12b76a"
  warning: "#f59e0b"
  danger: "#e5484d"
  info: "#6d4aff"
  status-mock-bg: "#fef3c7"
  status-mock-fg: "#92400e"
  status-planned-bg: "#f1f5f9"
  status-planned-fg: "#334155"
  status-development-bg: "#dbeafe"
  status-development-fg: "#1d4ed8"
  status-integrated-bg: "#f3e8ff"
  status-integrated-fg: "#7e22ce"
  status-production-bg: "#dcfce7"
  status-production-fg: "#166534"
  dark-background: "#0e1116"
  dark-surface: "#1a1d24"
  dark-on-surface: "#f1f5f9"
  dark-on-surface-muted: "#94a3b8"
typography:
  display-hero:
    fontFamily: ui-sans-serif, system-ui, sans-serif
    fontSize: 4.5rem
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "-0.03em"
  headline-xl:
    fontFamily: ui-sans-serif, system-ui, sans-serif
    fontSize: 2rem
    fontWeight: 800
    lineHeight: 2.5rem
    letterSpacing: "-0.025em"
  headline-lg:
    fontFamily: ui-sans-serif, system-ui, sans-serif
    fontSize: 1.5rem
    fontWeight: 800
    lineHeight: 2rem
    letterSpacing: "-0.02em"
  title-lg:
    fontFamily: ui-sans-serif, system-ui, sans-serif
    fontSize: 1.25rem
    fontWeight: 700
    lineHeight: 1.75rem
    letterSpacing: "-0.01em"
  title-md:
    fontFamily: ui-sans-serif, system-ui, sans-serif
    fontSize: 1.125rem
    fontWeight: 700
    lineHeight: 1.75rem
    letterSpacing: "-0.01em"
  title-sm:
    fontFamily: ui-sans-serif, system-ui, sans-serif
    fontSize: 0.9375rem
    fontWeight: 700
    lineHeight: 1.25rem
  body-lg:
    fontFamily: ui-sans-serif, system-ui, sans-serif
    fontSize: 1rem
    fontWeight: 400
    lineHeight: 1.5rem
  body-md:
    fontFamily: ui-sans-serif, system-ui, sans-serif
    fontSize: 0.875rem
    fontWeight: 400
    lineHeight: 1.25rem
  body-sm:
    fontFamily: ui-sans-serif, system-ui, sans-serif
    fontSize: 0.8125rem
    fontWeight: 700
    lineHeight: 1rem
  label-md:
    fontFamily: ui-sans-serif, system-ui, sans-serif
    fontSize: 0.875rem
    fontWeight: 500
    lineHeight: 1.25rem
  label-sm:
    fontFamily: ui-sans-serif, system-ui, sans-serif
    fontSize: 0.75rem
    fontWeight: 600
    lineHeight: 1rem
    letterSpacing: "0.05em"
  code-sm:
    fontFamily: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace
    fontSize: 0.875rem
    fontWeight: 400
    lineHeight: 1.25rem
rounded:
  xs: 4px
  sm: 6px
  md: 8px
  lg: 12px
  xl: 16px
  2xl: 20px
  3xl: 24px
  pill: 9999px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  2xl: 32px
  3xl: 40px
  4xl: 64px
  container-padding: 32px
layout:
  container-max: 1440px
  shell-header-height: 64px
  shell-sidebar-expanded: 248px
  shell-sidebar-collapsed: 72px
motion:
  fast: 150ms
  normal: 200ms
  slow: 300ms
  panel: 500ms
  easing-standard: cubic-bezier(0.4, 0, 0.2, 1)
elevation:
  shell-blur: 12px
  shell-opacity: "0.70"
  shell-border-opacity: "0.10"
  chart-fill-opacity: "0.60"
shadows:
  subtle: "0 1px 2px rgba(16, 24, 40, 0.04), 0 12px 32px -16px rgba(16, 24, 40, 0.18)"
  raised: "0 2px 4px rgba(16, 24, 40, 0.06), 0 18px 40px -16px rgba(16, 24, 40, 0.24)"
  dialog: "0 20px 25px rgba(16, 24, 40, 0.10), 0 8px 10px rgba(16, 24, 40, 0.04)"
components:
  shell-sidebar:
    backgroundColor: "#ffffff"
    textColor: "#14171c"
    rounded: 0px
    width: 248px
    padding: 16px
  shell-header:
    backgroundColor: "#f4f4f6"
    textColor: "#14171c"
    rounded: 0px
    height: 64px
    padding: "0 16px"
  nav-item:
    backgroundColor: transparent
    textColor: "#475569"
    rounded: 12px
    height: 40px
    padding: "8px 12px"
  nav-item-active:
    backgroundColor: "#14171c"
    textColor: "#ffffff"
    rounded: 12px
    height: 40px
    padding: "8px 12px"
  button-primary:
    backgroundColor: "#fe6e00"
    textColor: "#ffffff"
    rounded: 9999px
    height: 40px
    padding: "0 18px"
  button-primary-hover:
    backgroundColor: "#ea580c"
    textColor: "#ffffff"
  button-ghost-shell:
    backgroundColor: transparent
    textColor: "#475569"
    rounded: 9999px
    height: 40px
    width: 40px
  input-shell:
    backgroundColor: "#eef0f3"
    textColor: "#14171c"
    rounded: 9999px
    height: 40px
    padding: "0 16px"
  card-panel:
    backgroundColor: "#ffffff"
    textColor: "#1f2937"
    rounded: 24px
    padding: 24px
  table-header:
    backgroundColor: "#ffffff"
    textColor: "#64748b"
    height: 44px
    padding: "0 16px"
  table-cell:
    backgroundColor: transparent
    textColor: "#1f2937"
    padding: "10px 16px"
  status-badge-mock:
    backgroundColor: "#fef3c7"
    textColor: "#92400e"
    rounded: 9999px
    padding: "4px 10px"
  status-badge-planned:
    backgroundColor: "#f1f5f9"
    textColor: "#334155"
    rounded: 9999px
    padding: "4px 10px"
  status-badge-development:
    backgroundColor: "#dbeafe"
    textColor: "#1d4ed8"
    rounded: 9999px
    padding: "4px 10px"
  status-badge-integrated:
    backgroundColor: "#f3e8ff"
    textColor: "#7e22ce"
    rounded: 9999px
    padding: "4px 10px"
  status-badge-production:
    backgroundColor: "#dcfce7"
    textColor: "#166534"
    rounded: 9999px
    padding: "4px 10px"
  login-panel:
    backgroundColor: "#fe6e00"
    textColor: "#ffffff"
    rounded: 0px
    padding: 32px
  login-card:
    backgroundColor: "#ffffff"
    textColor: "#14171c"
    rounded: 24px
    padding: 32px
---

## Overview

StockFlow is a warehouse system: operators scan, count and place goods all day, and the office captures invoices and watches the operation. The interface is a **light bento workspace**: a soft gray canvas holds borderless white cards with 24px corners, pastel tiles carry the indicators, and a single bold orange accent marks what matters. It is the same visual language as the AXZY UI System's `appearance="bento"`, adapted to the StockFlow brand.

The app must feel fast and calm: air on dashboards, density in tables, and never a decorative hero section over the work.

- **Style:** Bento, light-first, pastel tiles, bold typography, orange accent.
- **Era:** 2020s dashboard modern.
- **Light/Dark:** ✓ Full / ✓ Full.

## Color

- **Canvas** is `#F4F4F6`; content lives on **white cards** with no visible border, a diffuse shadow and a 24px radius.
- **Ink** is `#14171C`: active pills, primary text and the dark control. Orange is an **accent**, not a surface: primary buttons, the active state, links, focus rings, chart strokes.
- **Pastel lives in the tiles**, never in saturated fills. The kit's bento tones (`plain`, `sea`, `lavender`, `mint`, `sun`) and `ITCard tone` are the only way to paint a colored card. One tone per meaning, always the same meaning: reception = mint, ready to place = sea, discrepancies = sun, orders = lavender, cancelled = rose.
- Text on pastel is the tone's dark ink, never white. Text on the orange accent is white.
- Status colors come from the tokens `status-*-bg` / `status-*-fg`; never hardcode a red or green.

## Typography

- Display and headings: system sans, **800 weight**, tight tracking (`-0.02em` to `-0.03em`). Numbers are tabular.
- Body: 14px/20px, `#1F2937`; secondary text `#64748B`.
- Labels: 12px semibold, uppercase only for the small overlines.
- Codes and quantities: monospace `code-sm`, never proportional figures.

## Shape and elevation

- Radii: cards 24px, bento tiles 20px, controls and chips are full pills (`9999px`), fields 16px. Never a square card.
- No gradient, no border on cards. Shadows are diffuse and barely visible; a card is separated by the canvas, not by a line.
- Hover is a small lift (`-translate-y-0.5`) plus a slightly stronger shadow; active controls switch to ink.

## Layout

- The shell is the kit's `ITLayout` in bento: a **white sidebar** with ink-pill active items, and a top bar the color of the canvas with ink text. The content is a 1440px column with 32px padding.
- Dashboards use `ITBentoGrid` (12 columns, 84px rows, 10px gap) with `ITBentoTile`. A KPI tile is 3 columns; a wide tile is 6; a feed or a chart is 6 to 8.
- The home is a dashboard, never a menu: greeting, then the numbers of the operation, then the work queues and recent activity.

## Components

- **KPI tile:** label, icon, one big tabular value, a caption, one optional visual (`ITBentoGauge`, `ITBentoSpark`, `ITBentoWaves`, `ITBentoSegments`). Only with real data.
- **Panel card:** white card with an icon + title + description header, actions on the right, and an "open" round ink button when it leads somewhere.
- **Stat strip:** `ITBentoStatStrip` for a row of clickable counts that filter the list below.
- **Table:** the kit's `ITDataTable` stays dense (compact). No bento tiles inside a table.
- **States:** every data screen has loading (skeleton), empty (illustration + action) and error (message + retry). They are part of the screen, not an afterthought.

## Do not

- No dark glass shell, no gradients, no colored page backgrounds.
- No pastel card for a value that is zero or meaningless; use `plain`.
- No `!important` to fight the kit: the appearance decides cards, inputs and nav.
- No new color chosen in a screen: everything comes from these tokens.
