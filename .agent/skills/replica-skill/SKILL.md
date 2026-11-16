---
name: replica-skill
description: >-
  Eleven skills that clone any app clean-room style: reverse-engineer it, plan the stack, rebuild the design system, build frontend and backend, test for bugs, compute parity score, research real user complaints, rebrand, and deploy. Use when the user mentions "replica", "replica-skill", "replica skill", "clone an app", or wants to replicate any application cleanly.
---

# Replica Skill Pack

A suite of 11 clean-room skills to reverse-engineer, rebuild, rebrand, and ship a high-quality clone of any application or SaaS.

## Pipeline Overview

Each step reads what the previous step wrote into the `replica/` directory in the project:

```
replica-recon -> replica-architect -> replica-design -> replica-build -> replica-backend -> replica-test -> replica-diff -> replica-entrepreneur -> replica-brand -> replica-launch -> replica-deploy
```

| Step | Skill | Command / Trigger | What It Does |
|---|---|---|---|
| 1 | `replica-recon` | `/replica-recon` | Reverse-engineers any app: screens, flows, components, data model (`replica/recon.md`, `replica/features.csv`). |
| 2 | `replica-architect` | `/replica-architect` | Plans the stack, database schema, API routes, and build order. |
| 3 | `replica-design` | `/replica-design` | Extracts design tokens (colors, type, spacing, components) and checks WCAG contrast (`tokens.json`, `contrast.py`). |
| 4 | `replica-build` | `/replica-build` | Rebuilds the frontend screen-by-screen, updating the feature matrix. |
| 5 | `replica-backend` | `/replica-backend` | Auth, database migrations, payments (Stripe), email, integrations. |
| 6 | `replica-test` | `/replica-test` | Generates test plans, Playwright e2e specs, and logs bug reports. |
| 7 | `replica-diff` | `/replica-diff` | Computes feature parity score (`parity.py`) and visual edge diff (`imgdiff.py`). |
| 8 | `replica-entrepreneur` | `/replica-entrepreneur` | Analyzes public user reviews, extracts common pain points, and defines a USP (`reviews.py`). |
| 9 | `replica-brand` | `/replica-brand` | Names, selects distinct palettes, and sweeps the codebase to purge original identifiers (`sweep.py`). |
| 10 | `replica-launch` | `/replica-launch` | Landing page copy, pricing comparison, and App Store / Play Store listing linting (`listing.py`). |
| 11 | `replica-deploy` | `/replica-deploy` | Runs preflight checks (tests, sweep, parity) and guides production deployment. |

## Built-In Python Tools

The Python tools are located in `.agent/skills/replica-<name>/` and require only standard-library Python (>=3.8):

- **Image Diff**: `python .agent/skills/replica-diff/imgdiff.py original.png clone.png --out diff.png`
- **Feature Parity**: `python .agent/skills/replica-diff/parity.py replica/features.csv`
- **User Review Analyzer**: `python .agent/skills/replica-entrepreneur/reviews.py replica/reviews.csv`
- **WCAG Contrast Checker**: `python .agent/skills/replica-design/contrast.py replica/design/tokens.json`
- **Brand Sweep**: `python .agent/skills/replica-brand/sweep.py . --avoid "Original App"`
- **Store Listing Linter**: `python .agent/skills/replica-launch/listing.py replica/launch/listing.json`

## Testing the Tools

Run the suite anytime:
```powershell
python -m unittest discover -s .agent/skills/replica-skill/tests -v
```
