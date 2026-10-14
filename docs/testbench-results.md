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
