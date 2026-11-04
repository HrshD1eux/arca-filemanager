# UI and Copy Standards for ARCA File Manager

## 1. Copywriting & Marketing Claims
- **Never use "100%" claims**: Strictly avoid hyperbolic phrases like "100% offline", "100% secure", "100% private", "100% optional", or "impossible to hack".
- **Use grounded, factual engineering descriptions**:
  - Instead of "100% offline", use "Fully offline-first" or "Completely isolated from network services".
  - Instead of "100% optional", use "Completely optional" or "Optional".
  - Instead of "100% user-initiated", use "Exclusively user-initiated".
- **Acknowledge physical hardware realities**: E.g., for file shredding, truthfully note modern flash controller wear leveling and TRIM constraints rather than promising impossible guarantees.

## 2. Visual Boundaries & Stroke Widths
- **Never use thick boundaries**: Avoid heavy, chunky, or clumsy border strokes (such as 2dp+ solid borders).
- **Use subtle hairline strokes**: Keep all card, dialog, and container borders at `<= 0.5.dp` (or at most `1.dp`) using subtle alpha tints like `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)` or `0.5f`.
- **Button Affordance**: Buttons must have clear, distinct formatting (subtle surface container color, proper rounded shapes, or subtle 1dp hairline outlines) so users instantly understand clickable boundaries without excessive visual noise.
