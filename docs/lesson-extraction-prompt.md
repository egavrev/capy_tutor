# Capy Tutor: lesson extraction prompt

How to use: open Claude, Gemini (AI Studio) or ChatGPT, paste everything below the line, then attach or paste the source (textbook photos, a PDF, copied text, your notes). Save the Markdown block it returns as a `.md` file in Google Drive › Capy Tutor › 04 Lessons, check the key ideas, and import it into the app.

---

You prepare lessons for **Capy Tutor**, an app where a 5th grader in Moldova retells a textbook lesson out loud and the app checks which key ideas she remembered. From the source I give you, produce **one Markdown file per lesson** in the exact format below. The app reads this file automatically, so the format must be exact.

## Output format

```markdown
---
subject: <Istorie | Română | Matematică | Științe | Geografie>
grade: 5
chapter: <chapter or unit name from the source, or leave empty>
title: <lesson title>
language: ro
---

# <lesson title>

<lesson text in paragraphs>

## Idei cheie

- <key idea 1>
- <key idea 2>

## Termeni

- **<term>**: <short definition>
```

## Rules for the lesson text

- Use only what is in the source. Never add facts, dates, names or explanations that the source does not contain.
- Keep the textbook's own wording and order as much as possible. Fix only obvious scanning errors, broken lines and hyphenation.
- Remove page numbers, headers, footers, exercise blocks ("Activitate", "Aplică", "Verifică-ți cunoștințele"), questions to the student and "see page X" references.
- Keep a picture caption or a "Știai că?" box only if it states a fact the lesson relies on; then write it as a normal sentence in the right place.
- Turn tables and timelines into short sentences ("În anul 1359 s-a format Țara Moldovei.").
- Write proper Romanian with the comma-below letters **ș ț Ș Ț**, never the cedilla forms ş ţ, and correct ă â î.
- Aim for 150–900 words. If the source holds several lessons, make one separate file for each.

## Rules for the key ideas (`## Idei cheie`)

These are what the app checks the child's retelling against, so they matter most.

- 5–12 ideas per lesson: about one idea per 60–80 words of text.
- One fact per idea. One short sentence, at most 15 words, in simple Romanian a 10-year-old would use.
- State the fact itself ("Lucy este o fosilă găsită în Etiopia."), never describe the lesson ("Lecția vorbește despre Lucy.").
- Keep exact names, dates, numbers and places as written in the source.
- Cover the whole lesson, in the order the lesson presents it, so no paragraph's main point is missing.
- Choose what a teacher would ask in class: causes, consequences, definitions, who/what/when/where, steps of a process. Skip minor details and examples that only illustrate a point.
- No two ideas may say the same thing in different words.

## Rules for the terms (`## Termeni`)

- 0–8 new or specialist words from the lesson, each with a definition of at most 12 words, taken from the source when it gives one.
- If the lesson has no new terms, keep the heading and leave the list empty.

## The other fields

- `subject`: exactly one of Istorie, Română, Matematică, Științe, Geografie.
- `chapter` and `title`: as printed in the source. If there is no title, write a short one (at most 6 words) and flag it.
- `grade` stays 5 and `language` stays ro unless I say otherwise.

## Before you answer, check

1. Every key idea can be found in the lesson text.
2. Every paragraph's main point is covered by at least one key idea.
3. The count is 5–12 and each idea has at most 15 words.
4. Only ș ț (comma-below), no ş ţ.
5. Nothing is added that is not in the source.

## How to reply

- Put each lesson file in its own ```markdown code block, nothing else inside the block.
- Above each block, suggest a file name like `istorie-aparitia-omului.md` (lower case, no diacritics, hyphens).
- After the blocks, at most 3 short lines on anything I should check: unreadable parts of the source, a title you had to write yourself, or facts you were unsure of. If everything was clear, write "Nimic de verificat."

The source follows.
