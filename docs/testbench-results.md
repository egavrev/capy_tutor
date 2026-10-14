# Test bench results (milestone M1)

Groq free plan, `openai/gpt-oss-120b`, 2 sample lessons × 5 sample retellings each (`tools/testbench/samples/`). Latest full reports: the [`testbench`](https://github.com/egavrev/capy_tutor/tree/testbench) branch.

## Gap marking vs the parent's marks

A retelling agrees when Capy and the parent put every idea on the same side: *known* (covered) or *to practise* (partial, missed, wrong). Target (HANDOFF §13): at least 8 of 10.

| Run | Prompts | Reasoning (enrich / assess) | Agrees | Disagreements |
|---|---|---|---|---|
| 1 | v1 / v1 | low / low | 7 of 10 | 4 ideas marked *covered* where a detail was missing (Lucy's age, "24 de ore", "turtită la poli", what fire did) |
| 3 | v2 / v2 | medium / low | **8 of 10** | "urme de oase păstrate în pământ" → partial (parent: covered); "se învârte și avem zi și noapte" → covered (parent: partial) |
| 4 | v2 / v2 | medium / low | **9 of 10** | "luna e satelitul lui" → partial (parent: covered; Capy wants "singurul") |

Run 2 stopped on an HTTP timeout bug (fixed: OkHttp's 10 s read timeout).

**Missed and wrong ideas were judged correctly in every run.** All remaining disagreements are on the covered/partial border, and they change between runs: these are judgment calls on how strict Capy should be.

## Generated hints (enrich_ideas)

- v1: clues often gave the answer ("Se numește Ecuator", "Este aproximativ 365 de zile"); explanations repeated the idea.
- v2: give-aways down to 0–3 of 16 clues per run, flagged in the report ("Reveals answer?"). The prompt alone does not guarantee it.
- Keyword groups sometimes include context words ("Pământul / planeta") and odd spellings ("sateliț"); explanations still lean on the lesson's own wording.

## Cost and speed (per call, runs 3–4)

| Call | Latency | Tokens in / out |
|---|---|---|
| enrich_ideas (medium reasoning, once per lesson) | 8–10 s | ~1,750 / ~3,700 |
| assess_recall (low reasoning, once per retelling) | 2.2–2.4 s avg, 3.7 s max | ~1,575 / ~700 |

One retelling check is ~2,300 tokens, far inside the 40,000-token session budget. The bench hits the free plan's 8,000 tokens/minute because it sends 10 retellings back to back; it waits and continues. A real session (one retelling, then answers) stays below that.

## Open points for M2

- Clues that still reveal the answer: detect them on the phone (as the bench does) and fall back to a safe clue ("Primul cuvânt începe cu litera …"), so the hint ladder never gives the answer before step 3.
- Enrichment needs a longer timeout than the 20 s session calls (it takes 8–10 s; use 60 s at import).
- How strict Capy should be on the covered/partial border is the owner's call (see the disagreements above).
- `transcribe` has not run yet: it needs a real recording, kept on the Mac.

# Whole sessions (milestone M2)

Scripted children (`tools/testbench/samples/*.session.md`) run complete sessions against Groq: retelling, coverage, hint ladder, final test, summary. Reports: `*.session.md` on the `testbench` branch.

| Run | Istorie | Geografie | Calls / tokens per session | Pauses |
|---|---|---|---|---|
| 1 (assess_answer v1) | 4 din 8 · DONE | 6 din 8 · DONE | 16–19 calls · 12–14k tokens | 9, all resumed |
| 2 (assess_answer v2, fill-in clue) | 3 din 8 · DONE | 6 din 8 · DONE | 20 calls · 15–16k tokens | 11, all resumed |

Both sessions stay inside the budget (25 calls, 40,000 tokens). The scripted child answers instantly, so it hits the free plan's 8,000 tokens/minute; every time, the session paused ("Capy e puțin obosit…"), was saved, and resumed with nothing lost.

**What works as specified:** gaps one at a time in lesson order; at most 3 hint steps; the explanation only at step 3; "Nu știu" moves on without judging; "Cred că am dreptate" accepted and flagged for the parent; score and parent note; save/restore at every step.

**Fixed during M2**
- Test answers were judged too strictly ("a treia", "un an" marked not yet). assess_answer v2 judges against what the question asks.
- Fallback clue: a fill-in-the-blank of the idea („Pământul are forma aproape a unei …, puțin … la poli.”) instead of a first-letter hint.

**Open: generated test questions are often inverted.** They contain the idea and ask for one word ("Care este a treia planetă de la Soare?" → "Pământul"), so they do not check that the child remembers the idea. Proposed fix (enrich_ideas v3): split each idea's keywords into *topic* (may appear in questions) and *answer* (what the child must say); the test question asks for the answer details; questions and clues are checked on the phone against the answer words.
