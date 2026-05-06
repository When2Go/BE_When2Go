---
description: Deep-dive spec review — interview-driven issue discovery and structured decision-making
argument-hint: <spec-file>
---

Read the file at $ARGUMENTS thoroughly before doing anything else. If the file cannot be read, ask for the correct path.

# Spec Quality Preferences (guide all recommendations by these)
- Every requirement should have one unambiguous interpretation by any implementer.
- Edge cases and error scenarios must be explicitly addressed, not left implicit.
- Define what's OUT of scope as clearly as what's in scope.
- Favor concrete examples over abstract descriptions.
- If a decision was made, document WHY — not just WHAT.

# Workflow

## STEP 1: Spec Interview
Using AskUserQuestion, interview me in depth across all dimensions: requirements clarity, architecture, technical feasibility, UI & UX, edge cases, constraints, and tradeoffs.

**Rules:**
- Questions must be non-obvious, deeply probing, and specific to this spec — never generic or boilerplate.
- Skip dimensions the spec already covers well; focus on gaps.
- Number each question (Q1, Q2, Q3...).
- Build on previous answers — never repeat covered ground.
- If my answer is vague, push back and dig deeper.
- Do NOT assume my priorities on timeline or scale — ask.
- Continue until all dimensions are sufficiently covered.

## STEP 2: Issue Review
Synthesize interview findings into issues, organized by section:
1) Requirements Clarity — ambiguous language, multiple interpretations, missing acceptance criteria
2) Completeness — missing edge cases, error scenarios, boundary conditions, undefined behaviors
3) Architecture & Feasibility — technical risks, unrealistic constraints, unresolved dependencies
4) Scope & Boundaries — unclear in/out of scope, scope creep risks, undefined limits
5) UX & User Flows (if applicable) — incomplete flows, missing error/loading/empty states

**Issue format:**
- Number issues (1-1, 1-2 = section 1, issue 1 and 2).
- Describe the problem concretely with spec references.
- Present 2-3 options with LETTERS (A, B, C). Include "do nothing" where reasonable.
- For each option: effort to spec out, risk if left unaddressed, impact on implementation.
- **Recommended option is always A**, with reasoning mapped to my preferences above.
- In AskUserQuestion, label as `"1-1A: recommended approach", "1-1B: alternative", "1-1C: do nothing"`.

**Pause after each section** — use AskUserQuestion for feedback before moving to the next.

## STEP 3: Spec Update
Once all decisions are made:
- Edit the original spec file in-place with all decisions, clarifications, and new details.
- Add or refine sections as needed (edge cases, error scenarios, scope boundaries, constraints, etc.).
- Mark open items with `> [!WARNING] OPEN ITEM:` callouts.
- Do NOT remove existing content unless explicitly agreed.
- Provide a brief summary of what changed.
