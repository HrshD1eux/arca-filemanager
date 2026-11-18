# UI, Design & Copy Standards for ARCA File Manager

## 1. Design Philosophy: Luxury & Precision Engineering
ARCA is a high-performance, precision Android file manager designed for professionals. It must look and feel like an industrial-grade, precision-milled physical instrument (similar to Apple hardware, Braun, Teenage Engineering, Linear, and Things 3)—**never** like a cartoon, casual mobile game, or budget utility app.

### A. Color Discipline (Ban the "Circus / Skittles" Palette)
- **Neutral Foundation**: 90% of the UI must rely on disciplined monochrome/slate/graphite tones.
- **Surgical Accents**: Color is an intentional signal, not decorative confetti. Avoid flashing 8 competing high-saturation primary colors on a single screen.
- **Harmonic Semantic Tones**: When category or status colors are required, use refined, cohesive tones (e.g., Titanium Blue, Deep Emerald, Warm Amber, Rosewood) with muted opacity rather than harsh neon primaries.

### B. Micro-Interactions & Physics (Ban Cartoon "Bounce" Rubber Physics)
- **No Rubbery Bouncing**: Never use bouncy spring physics (`Spring.DampingRatioMediumBouncy`) or extreme scale-down factors (such as 0.94x–0.96x) on clickable cards or buttons. They feel like a casual gacha game or rubber duck.
- **Precision Mechanical Response**: Interactions must feel like a precision mechanical switch: instantaneous response (`Spring.DampingRatioNoBouncy`, `Spring.StiffnessHigh`), an imperceptible 1% micro-flex (`0.99f`), subtle tonal/alpha dip (`0.90f`–`0.94f`), and crisp micro-haptic ticks.

### C. Geometric Discipline & Corner Radii Scale
- **No "Bubblewrap" Bloat**: Small cards and tiles must never have bulbous 14dp–16dp radii that make them look like soft marshmallows.
- **Strict 3-Tier Radii System**:
  - **Sheets, Dialogs & Full Containers**: `12.dp` – `14.dp` maximum.
  - **Cards, Action Tiles & Content Sections**: `8.dp` – `10.dp`.
  - **Inner Chips, Badges & Small Controls**: `4.dp` – `6.dp`.
- **Hairline Strokes**: Pair cards and elevated surfaces with crisp `0.5.dp` hairline borders (`outlineVariant.copy(alpha = 0.4f)`).

### D. Iconography & Badging
- **De-Box the Glyphs**: Avoid trapping every icon inside a chunky, hyper-saturated solid square badge. Icons should have breathing room.
- **Refined Two-Tone Containers**: When an icon container is required, use ultra-subtle tinting (`alpha = 0.08f`–`0.10f`), clean `6.dp`–`8.dp` corner radius, and hairline borders, allowing the glyph to shine without visual clutter.

### E. Telemetry & Progress Gauges
- **Slender Precision Meters**: Never use 10dp+ thick "sausage" progress bars or garish rainbow segments.
- **Architectural Gauges**: Telemetry meters (storage gauges, transfer progress) must be slender (`3.dp` to `4.dp`), with subtle corner rounding (`2.dp`) and clean, tabular monospace labels.

### F. Typographic Restraint
- **Tight Title Tracking**: Use tight letter-spacing on headlines (`-0.5.sp` to `-0.2.sp`) for an authoritative editorial feel.
- **Uppercase Micro-Labels**: Use tracked uppercase typography for section headers and badges (`letterSpacing = 0.6.sp` to `0.8.sp`).
- **Tabular Figures**: Always format storage capacities, file sizes, and counters with tabular numeric alignment or monospace fonts for professional visual structure.

---

## 2. Copywriting & Marketing Claims
- **Never use "100%" claims**: Strictly avoid hyperbolic phrases like "100% offline", "100% secure", "100% private", "100% optional", or "impossible to hack".
- **Use grounded, factual engineering descriptions**:
  - Instead of "100% offline", use "Fully offline-first" or "Completely isolated from network services".
  - Instead of "100% optional", use "Completely optional" or "Optional".
  - Instead of "100% user-initiated", use "Exclusively user-initiated".
- **Acknowledge physical hardware realities**: E.g., for file shredding, truthfully note modern flash controller wear leveling and TRIM constraints rather than promising impossible guarantees.

---

## 3. Visual Boundaries & Stroke Widths
- **Never use thick boundaries**: Avoid heavy, chunky, or clumsy border strokes (such as 2dp+ solid borders).
- **Use subtle hairline strokes**: Keep all card, dialog, and container borders at `<= 0.5.dp` (or at most `1.dp`) using subtle alpha tints like `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)` or `0.5f`.
- **Button Affordance**: Buttons must have clear, distinct formatting (subtle surface container color, proper rounded shapes, or subtle 1dp hairline outlines) so users instantly understand clickable boundaries without excessive visual noise.
