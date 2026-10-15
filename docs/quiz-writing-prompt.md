# Prompt: write Capy Tutore quizzes for lessons

Paste everything below the line into the session that has the lesson `.md` files.

---

Write a quiz for each lesson file I give you (or for every `.md` lesson in the folder, except `TEMPLATE*`). The quizzes are for **Capy Tutore**, a calm study app for a 10-year-old girl in 5th grade in Moldova. She already retold the lesson to Capy; the quiz is a gentle review she plays later.

## Output

For each lesson `X.md`, write `X.quiz.json` next to it, **with the exact same file name stem** (for `istorie-aparitia-omului.md` → `istorie-aparitia-omului.quiz.json`). The app pairs them by name. At the end, zip all `.md` and `.quiz.json` files together into one zip.

Format (UTF-8 JSON, nothing else in the file):

```json
{"questions": [
  {"type": "choice", "ideas": ["i1"], "question": "Pe ce continent au trăit primii oameni?",
   "options": ["În Europa", "În Africa", "În Asia", "În America"], "answer": "În Africa",
   "explanation": "Primii oameni au trăit în Africa, apoi s-au răspândit în Asia și Europa."},
  {"type": "truefalse", "ideas": ["i8"], "statement": "Australopitecii știau să vorbească.", "answer": false,
   "explanation": "Australopitecii mergeau pe două picioare, dar aveau creier mic și nu vorbeau."},
  {"type": "order", "ideas": ["i8", "i9", "i10", "i12"], "question": "Pune-i în ordine, de la cel mai vechi la cel mai nou.",
   "items": ["Australopitecii", "Homo habilis", "Homo erectus", "Homo sapiens sapiens"],
   "explanation": "Întâi australopitecii, apoi omul îndemânatic, omul cu mers vertical și, la urmă, omul înțelept."},
  {"type": "match", "ideas": ["i3", "i4", "i7"], "question": "Potrivește fiecare teorie cu ce spune ea.",
   "pairs": [{"left": "Teoria creaționistă", "right": "O divinitate a creat omul"},
             {"left": "Teoria evoluționistă", "right": "Omul a apărut treptat"},
             {"left": "Teoria extraterestră", "right": "Altă civilizație a venit pe Pământ"}],
   "explanation": "Fiecare teorie încearcă să explice în felul ei de unde vine omul."}
]}
```

- `ideas`: which key ideas the question checks. The ids are the position in the lesson's `## Idei cheie` list: the first bullet is `"i1"`, the second `"i2"`, and so on. Only ids that exist in that lesson.
- `choice`: `answer` is the **exact text** of the right option (not a letter or number).
- `truefalse`: the sentence goes in `statement`; `answer` is `true` or `false` without quotes.
- `order`: `items` are written **in the correct order**; the app shuffles them.
- `match`: each pair is written **correctly matched**; the app shuffles the right side.

## Hard rules (the app rejects the quiz otherwise)

- 5 to 10 questions per lesson; aim for 8.
- At least two types per quiz; use three or four when the lesson allows it.
- `choice`: 3 or 4 options, all different, exactly one right.
- `order`: 3 to 5 items, all different. `match`: 3 or 4 pairs, all lefts different, all rights different.
- Lengths: `question`/`statement` up to 220 characters; each option, item, `left`, `right` up to 90; `explanation` up to 300.
- No two questions with the same text.
- Romanian with **ș ț (comma below)**, never ş ţ (cedilla).

## How to write them well

- **Only facts from the lesson**: its `## Idei cheie` first, then the lesson text and `## Termeni`. Nothing from outside the lesson.
- **Cover the lesson**: spread the questions over as many key ideas as you can; no idea more than twice.
- Language for a 10-year-old: short sentences, everyday words, second person singular when you address her.
- `choice`: wrong options must be plausible and from the same lesson or topic (other places, other people, other dates of the lesson), about the same length as the right one. No "toate variantele", no "niciuna", no trick questions, no "Care NU…".
- `truefalse`: about half true and half false. A false statement changes **one** clear fact of a true one (a place, a person, a number). No double negatives, no "întotdeauna/niciodată" traps.
- `order`: only when there is an objective order (dates, periods, steps of a process, before/after in the lesson). Say the direction in the question ("de la cel mai vechi la cel mai nou"). Good for history: periods, rulers, events with years.
- `match`: term ↔ meaning (from `## Termeni`), person ↔ deed, place ↔ fact, people ↔ invention. Every left must match only one right.
- `explanation`: one or two kind sentences that state the fact again, so she learns it whether she was right or not. Never "Greșit" or "Ai greșit"; no exclamation-mark scolding. No timers, no "repede".
- A good mix for 8 questions: about 3 `choice`, 2–3 `truefalse`, 1 `order` (when the lesson has an order), 1–2 `match`.

## Check before you finish

Run the checker that the app's rules come from (it reads each `X.quiz.json` and the `X.md` next to it):

```
curl -fsSLO https://raw.githubusercontent.com/egavrev/capy_tutor/main/tools/quiz-check.py
python3 quiz-check.py <folder with the lessons>
```

Fix every problem it prints until it says `0 with problems`. If you cannot download it, check every hard rule above by hand for each file, especially: the idea ids exist, each `choice` answer is copied exactly from its options, and `truefalse` answers are `true`/`false` without quotes.

Then read each quiz once more as the child would: is every question clear, does it have exactly one right answer, and is that answer really in the lesson?
