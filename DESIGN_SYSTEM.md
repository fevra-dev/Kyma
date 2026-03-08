# SonicVault Design System

Design language inspired by **Dieter Rams** and **Braun T3**: warm neutrals, layered depth, honest materials. No cold blue-white; no decoration for decoration's sake. *"Less, but better."*

---

## Color Palettes

### Dark Mode — Braun T3 Warm Charcoal

The primary palette. Warm charcoal chassis with aged-paper text — exactly what Rams used on dark Braun products.

| Role       | Hex       | RGB              | Use                                    |
|------------|-----------|------------------|----------------------------------------|
| **Chassis** | `#0A0907` | 10, 9, 7         | Base background — warm charcoal, back of a Braun T3 |
| **Panels** | `#100E0B` | 16, 14, 11       | Surfaces above chassis — you feel the depth |
| **UI Elements** | `#161310` | 22, 19, 16  | Cards, containers — dark walnut        |
| **Text**   | `#D4CCBC` | 212, 204, 188    | Primary text — aged paper, warm. Not white |

**Material 3 mapping (dark):**
- `surface` → #0A0907 (chassis)
- `surfaceContainerLow` → #100E0B (panels)
- `surfaceContainer` → #161310 (UI elements)
- `onSurface` → #D4CCBC (text)

---

### Light Mode — Inverse

Inverted hierarchy: chassis becomes light, text becomes dark. Same warm family — no cold blue-white.

| Role       | Hex       | Use                                    |
|------------|-----------|----------------------------------------|
| **Chassis** | `#F5F3F0` | Base background — warm off-white, aged paper |
| **Panels** | `#EFECE8` | Surfaces above chassis — subtle depth  |
| **UI Elements** | `#E8E5E0` | Cards, containers — light walnut  |
| **Text**   | `#0A0907` | Primary text — warm charcoal (same as dark chassis) |

**Material 3 mapping (light):**
- `surface` → #F5F3F0 (chassis)
- `surfaceContainerLow` → #EFECE8 (panels)
- `surfaceContainer` → #E8E5E0 (UI elements)
- `onSurface` → #0A0907 (text)

---

## Contrast & Accessibility

- **Dark mode:** #D4CCBC on #0A0907 — ~12:1 contrast (AAA)
- **Light mode:** #0A0907 on #F5F3F0 — ~14:1 contrast (AAA)
- Secondary text (`onSurfaceVariant`): ensure 4.5:1 minimum on its background

---

## Principles

1. **Layered depth** — Chassis → Panels → UI elements. Never flatten.
2. **Honest materials** — Warm neutrals only. No fake gradients or cold whites.
3. **Aged paper** — Text should feel like ink on paper, not screen glow.
4. **Inversion** — Light mode mirrors dark mode structurally; only the values flip.
