#!/usr/bin/env python3
"""
Enrich the packaged RoadWords vocabulary database.

Adds second/third context examples to existing words and recalculates
frequency_rank from wordfreq when available. The Gemini API key is read from
GEMINI_API_KEY and is never stored in the database.
"""

import bisect
import json
import os
import sqlite3
import sys
import time
import urllib.request

DB_PATH = "/home/cesar/php/tutor/RoadWordsApp/app/src/main/assets/vocabulary.db"
GEMINI_MODEL = "gemini-2.5-flash"
BATCH_SIZE = 35

EXTRA_COLUMNS = {
    "example_en_2": "TEXT DEFAULT ''",
    "example_en_3": "TEXT DEFAULT ''",
}


def ensure_columns(conn):
    existing = {row[1] for row in conn.execute("PRAGMA table_info(words)").fetchall()}
    for column, definition in EXTRA_COLUMNS.items():
        if column not in existing:
            conn.execute(f"ALTER TABLE words ADD COLUMN {column} {definition}")
    conn.commit()


def load_wordfreq_ranker():
    try:
        from wordfreq import top_n_list, zipf_frequency
    except Exception:
        return None

    top_words = top_n_list("en", 30000)
    exact_rank = {word: index + 1 for index, word in enumerate(top_words)}
    zipfs = [zipf_frequency(word, "en") for word in top_words]
    neg_zipfs = [-value for value in zipfs]

    def rank_for(text):
        normalized = text.strip().lower()
        if not normalized:
            return 30000
        if normalized in exact_rank:
            return exact_rank[normalized]

        z = zipf_frequency(normalized, "en")
        if z <= 0:
            return 30000
        return min(30000, max(1, bisect.bisect_left(neg_zipfs, -z) + 1))

    return rank_for


def recalculate_frequency_ranks(conn):
    rank_for = load_wordfreq_ranker()
    if rank_for is None:
        print("frequency_rank: wordfreq not available, keeping existing values")
        return

    rows = conn.execute("SELECT id, english FROM words").fetchall()
    for word_id, english in rows:
        conn.execute(
            "UPDATE words SET frequency_rank = ? WHERE id = ?",
            (rank_for(english), word_id),
        )
    conn.commit()
    print(f"frequency_rank: recalculated {len(rows)} rows with wordfreq")


def fetch_rows_to_enrich(conn):
    return conn.execute(
        """
        SELECT id, english, spanish, cefr_level, is_phrasal_verb, part_of_speech,
               category, example_en
        FROM words
        WHERE COALESCE(example_en_2, '') = ''
           OR COALESCE(example_en_3, '') = ''
        ORDER BY cefr_level ASC, frequency_rank ASC, id ASC
        """
    ).fetchall()


def build_prompt(rows):
    payload = []
    for row in rows:
        payload.append(
            {
                "id": row[0],
                "english": row[1],
                "spanish": row[2],
                "cefr_level": row[3],
                "is_phrasal_verb": bool(row[4]),
                "part_of_speech": row[5],
                "category": row[6],
                "existing_example_en": row[7],
            }
        )

    return f"""You are enriching an English-Spanish vocabulary database for Spanish speakers learning English.

For each item, create:
- example_en_2: a clear natural English sentence under 12 words
- example_en_3: another clear natural English sentence under 12 words

Rules:
- The English examples must use the exact English word or phrase.
- Do not repeat the existing example.
- Keep examples practical and context-rich.
- Match the part of speech.
- Avoid rare names and literary language.
- Return ONLY a valid JSON array.
- Keep the original id exactly.

Items:
{json.dumps(payload, ensure_ascii=False)}

Expected output shape:
[
  {{
    "id": 123,
    "example_en_2": "...",
    "example_en_3": "..."
  }}
]"""


def call_gemini(api_key, rows):
    url = (
        "https://generativelanguage.googleapis.com/v1beta/models/"
        f"{GEMINI_MODEL}:generateContent?key={api_key}"
    )
    body = {
        "contents": [{"parts": [{"text": build_prompt(rows)}]}],
        "generationConfig": {
            "temperature": 0.45,
            "responseMimeType": "application/json",
            "thinkingConfig": {"thinkingBudget": 0},
        },
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=90) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    text = data["candidates"][0]["content"]["parts"][0]["text"]
    result = json.loads(text)
    if not isinstance(result, list):
        raise ValueError("Gemini returned non-list JSON")
    return result


def fallback_examples(row):
    word_id, english, _spanish, _level, is_pv, pos, _category, existing_en = row
    term = english.strip()
    pos = (pos or "").lower()

    if pos == "adjective":
        en2 = f"The answer was {term}."
        en3 = f"Keep your message {term}."
    elif pos == "noun":
        en2 = f"The {term} became important today."
        en3 = f"We discussed the {term} in class."
    elif pos == "adverb":
        en2 = f"She spoke {term} during the meeting."
        en3 = f"He answered {term} and calmly."
    elif is_pv or " " in term:
        en2 = f"People often use \"{term}\" at work."
        en3 = f"I noticed \"{term}\" in the text."
    else:
        en2 = f"We need to {term} soon."
        en3 = f"They often {term} at work."

    if en2 == existing_en:
        en2 = f"I heard \"{term}\" in class."
    if en3 == existing_en or en3 == en2:
        en3 = f"Practice \"{term}\" in a short sentence."

    return {
        "id": word_id,
        "example_en_2": en2,
        "example_en_3": en3,
    }


def update_examples(conn, items):
    updated = 0
    for item in items:
        try:
            word_id = int(item["id"])
        except Exception:
            continue

        values = (
            str(item.get("example_en_2", "")).strip(),
            str(item.get("example_en_3", "")).strip(),
            word_id,
        )
        if not all(values[:2]):
            continue

        conn.execute(
            """
            UPDATE words
            SET example_en_2 = ?, example_en_3 = ?
            WHERE id = ?
            """,
            values,
        )
        updated += 1
    conn.commit()
    return updated


def main():
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    conn = sqlite3.connect(DB_PATH)

    ensure_columns(conn)
    recalculate_frequency_ranks(conn)

    rows = fetch_rows_to_enrich(conn)
    print(f"examples: {len(rows)} rows need enrichment")
    if not rows:
        conn.close()
        return

    total = 0
    for start in range(0, len(rows), BATCH_SIZE):
        batch = rows[start : start + BATCH_SIZE]
        batch_no = start // BATCH_SIZE + 1
        batch_count = (len(rows) + BATCH_SIZE - 1) // BATCH_SIZE
        print(f"examples: batch {batch_no}/{batch_count}", flush=True)

        if api_key:
            try:
                items = call_gemini(api_key, batch)
            except Exception as exc:
                print(f"  Gemini failed, using fallback for this batch: {exc}")
                items = [fallback_examples(row) for row in batch]
        else:
            items = [fallback_examples(row) for row in batch]

        total += update_examples(conn, items)
        time.sleep(0.4)

    remaining = len(fetch_rows_to_enrich(conn))
    print(f"examples: updated {total}, remaining {remaining}")
    conn.close()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
