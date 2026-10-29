---
name: parallel-dev-team
description: World-class multi-agent development orchestration skill. Use when the user says "use parallel agent", "use parallel dev", or wants multiple AI agents to collaborate as a large engineering team with zero file overwrites, strict file ownership boundaries, contract-first locking, and maximum speed.
---

# Parallel Development Team Orchestrator (`parallel-dev-team`)

## Activation Rule
- **Trigger Condition:** Activate when the user says **"use parallel agent"** or **"use parallel dev"**.

---

## The 4 Golden Rules for Zero-Overwrite Multi-Agent Work

1. **Absolute File Ownership:** No two agents are EVER allowed to write to the same file path. Every file belongs to exactly one agent stream.
2. **Contract-First Lock:** Shared data structures, TypeScript types, database schemas, and API contracts must be frozen in a shared contract file *before* parallel agents start coding.
3. **Protected Core Files:** Root configuration files (`package.json`, `tsconfig.json`, `build.gradle.kts`, `.env`, `Cargo.toml`) are locked. Parallel agents cannot touch them.
4. **Independent Scoped Verification:** Each agent tests only its assigned module using targeted commands rather than compiling the entire monolithic app simultaneously.

---

## Execution Workflow

### Step 1: Initialize the Architecture & Contract
Before spawning any parallel work, define the shared interface/contract:
- Create `docs/contracts/shared-types.md` or equivalent interface files.
- Freeze dependency additions. If a new library is needed, it must be installed on the main thread first.

### Step 2: Assign the Strict File Ownership Matrix
Divide the team into specialized streams with strict directory boundaries:

| Agent Role | Stream Name | Exclusive Write Scope (Allowed Paths) | Read-Only Access |
| :--- | :--- | :--- | :--- |
| **Architect / Engine** | `stream-core` | `core/**`, `engine/**`, `native/**`, `shaders/**` | `docs/contracts/**` |
| **Data & State Engineer** | `stream-data` | `data/**`, `database/**`, `storage/**`, `models/**` | `docs/contracts/**` |
| **UI & Feature Engineer** | `stream-ui` | `ui/**`, `components/**`, `screens/**`, `views/**` | `docs/contracts/**` |
| **QA & Testing Engineer** | `stream-qa` | `tests/**`, `cypress/**`, `__tests__/**` | All modules |

### Step 3: Prompt Template for Parallel Agents
When dispatching a sub-agent, give it this exact constraint structure:
> **"You are Agent [Role] working in stream [Stream Name].**
> - **Your Exclusive Write Scope:** Only create or edit files inside `[Path]`.
> - **Forbidden:** Do NOT touch any file outside your scope. Do NOT touch root config files or shared barrel `index.ts` files.
> - **Contract:** Adhere strictly to `docs/contracts/shared-types.md`.
> - **Done Criteria:** Run `[Scoped Test Command]` and ensure zero errors."

### Step 4: Integration & Barrel Export Phase
- Once all parallel streams complete their modules successfully, the Lead Orchestrator performs the final integration.
- Export all components through root `index.ts` or wire them into the main router/App entry point sequentially to prevent merge conflicts.