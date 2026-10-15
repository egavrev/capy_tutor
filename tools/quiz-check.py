#!/usr/bin/env python3
"""Checks <lesson>.quiz.json files with the same rules Capy Tutore uses on import (core/quiz/QuizSchema.kt).

    python3 quiz-check.py lessons/istorie            # every *.quiz.json next to its .md
    python3 quiz-check.py a.quiz.json b.quiz.json

Prints one line per problem and "ok" lines; exit code 1 when anything fails.
"""
import json, re, sys, unicodedata
from pathlib import Path

def fold(s):
    s = unicodedata.normalize("NFD", s.lower())
    return re.sub(r"\s+", " ", "".join(c for c in s if not unicodedata.combining(c))).strip()

def idea_ids(md_path):
    ideas, inside = [], False
    for line in Path(md_path).read_text(encoding="utf-8").splitlines():
        if line.startswith("## "):
            inside = fold(line[3:]).startswith("idei cheie")
            continue
        if inside:
            m = re.match(r"^\s*(?:[-*+]|\d+[.)])\s+(.*)$", line)
            if m and m.group(1).strip():
                ideas.append(m.group(1))
    return [f"i{n + 1}" for n in range(len(ideas))]

def check(quiz_path):
    p = Path(quiz_path)
    md = p.with_name(p.name[: -len(".quiz.json")] + ".md")
    if not md.exists():
        return [f"no lesson file {md.name} next to it"]
    known = set(idea_ids(md))
    errs = []
    def err(where, msg): errs.append(f"{where}: {msg}")
    def text(o, k, where, mx=10**9):
        v = o.get(k)
        if not isinstance(v, str) or not v.strip(): err(where, f'"{k}" is missing or empty'); return ""
        if len(v.strip()) > mx: err(where, f'"{k}" is too long ({len(v.strip())} > {mx})')
        if re.search("[şţŞŢ]", v): err(where, f'"{k}" uses ş/ţ with cedilla, write ș/ț')
        return v.strip()
    def texts(o, k, where, mx):
        v = o.get(k)
        if not isinstance(v, list): err(where, f'"{k}" must be a list'); return []
        out = []
        for x in v:
            if not isinstance(x, str) or not x.strip(): err(where, f'"{k}" has an empty entry'); continue
            if len(x.strip()) > mx: err(where, f'"{k}" entry too long ({len(x.strip())} > {mx})')
            if re.search("[şţŞŢ]", x): err(where, f'"{k}" uses ş/ţ with cedilla')
            out.append(x.strip())
        return out
    def distinct(vals, where, what):
        f = [fold(v) for v in vals]
        if len(set(f)) != len(f): err(where, f"{what} repeat")
    try:
        root = json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:
        return [f"invalid JSON: {e}"]
    qs = root.get("questions")
    if not isinstance(qs, list): return ['"questions" must be a list']
    if not 5 <= len(qs) <= 10: err("quiz", f"needs 5-10 questions, has {len(qs)}")
    types, prompts = set(), []
    for i, q in enumerate(qs):
        w = f"q{i + 1}"
        if not isinstance(q, dict): err(w, "must be an object"); continue
        t = q.get("type"); types.add(t)
        ideas = q.get("ideas")
        if not isinstance(ideas, list) or not ideas: err(w, '"ideas" must be a non-empty list of idea ids')
        else:
            for x in ideas:
                if x not in known: err(w, f'unknown idea "{x}" (the lesson has {sorted(known, key=lambda s: int(s[1:]))[-1] if known else "none"})')
        text(q, "explanation", w, 300)
        if t == "choice":
            prompts.append(text(q, "question", w, 220))
            opts = texts(q, "options", w, 90)
            if not 3 <= len(opts) <= 4: err(w, "a choice needs 3-4 options")
            distinct(opts, w, "options")
            a = text(q, "answer", w)
            if a and fold(a) not in [fold(o) for o in opts]: err(w, "the answer is not one of the options")
        elif t == "truefalse":
            prompts.append(text(q, "statement", w, 220))
            if not isinstance(q.get("answer"), bool): err(w, '"answer" must be true or false (no quotes)')
        elif t == "order":
            prompts.append(text(q, "question", w, 220))
            items = texts(q, "items", w, 90)
            if not 3 <= len(items) <= 5: err(w, "an order question needs 3-5 items")
            distinct(items, w, "items")
        elif t == "match":
            prompts.append(text(q, "question", w, 220))
            pairs = q.get("pairs")
            if not isinstance(pairs, list): err(w, '"pairs" must be a list'); continue
            if not 3 <= len(pairs) <= 4: err(w, "a match question needs 3-4 pairs")
            lefts = [text(pp, "left", w, 90) for pp in pairs if isinstance(pp, dict)]
            rights = [text(pp, "right", w, 90) for pp in pairs if isinstance(pp, dict)]
            distinct(lefts, w, "left side"); distinct(rights, w, "right side")
        else:
            err(w, f'unknown type "{t}" (choice, truefalse, order, match)')
    if len(types) < 2: err("quiz", "use at least two question types")
    f = [fold(x) for x in prompts if x]
    if len(set(f)) != len(f): err("quiz", "two questions are the same")
    return errs

def main(args):
    files = []
    for a in args or ["."]:
        pa = Path(a)
        files += sorted(pa.glob("*.quiz.json")) if pa.is_dir() else [pa]
    bad = 0
    for f in files:
        errs = check(f)
        if errs:
            bad += 1
            for e in errs: print(f"{f.name}: {e}")
        else:
            print(f"ok {f.name}")
    print(f"{len(files)} quizzes, {bad} with problems")
    return 1 if bad else 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
