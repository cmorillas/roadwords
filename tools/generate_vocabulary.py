#!/usr/bin/env python3
"""
Generate a comprehensive vocabulary SQLite database for RoadWords.
Uses Gemini API to generate vocabulary in batches per CEFR level.
"""

import sqlite3
import json
import urllib.request
import urllib.error
import sys
import time
import os

API_KEY = os.environ.get("GEMINI_API_KEY", "")
DB_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "vocabulary.db"))
GEMINI_URL = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={API_KEY}"

# How many words per level
LEVEL_COUNTS = {
    "A1": 125,
    "A2": 110,
    "B1": 175,
    "B2": 170,
    "C1": 130,
    "C2": 90,
}

BATCH_SIZE = 40  # words per API call

EXTRA_COLUMNS = {
    "example_en_2": "TEXT DEFAULT ''",
    "example_en_3": "TEXT DEFAULT ''",
}

def ensure_extra_columns(conn):
    existing = {row[1] for row in conn.execute("PRAGMA table_info(words)").fetchall()}
    for column, definition in EXTRA_COLUMNS.items():
        if column not in existing:
            conn.execute(f"ALTER TABLE words ADD COLUMN {column} {definition}")
    conn.commit()

def create_db():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("""
        CREATE TABLE IF NOT EXISTS words (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            english TEXT NOT NULL UNIQUE,
            spanish TEXT NOT NULL,
            spanish_alts TEXT DEFAULT '',
            cefr_level TEXT NOT NULL,
            is_phrasal_verb INTEGER DEFAULT 0,
            part_of_speech TEXT DEFAULT '',
            category TEXT DEFAULT '',
            frequency_rank INTEGER DEFAULT 0,
            example_en TEXT DEFAULT '',
            example_en_2 TEXT DEFAULT '',
            example_en_3 TEXT DEFAULT ''
        )
    """)
    ensure_extra_columns(conn)
    conn.execute("CREATE INDEX IF NOT EXISTS idx_cefr ON words(cefr_level)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_phrasal ON words(is_phrasal_verb)")
    conn.commit()
    return conn

def generate_batch(level, batch_num, total_batches, phrasal_ratio=0.0):
    phrasal_count = int(BATCH_SIZE * phrasal_ratio)
    common_count = BATCH_SIZE - phrasal_count

    phrasal_instruction = ""
    if phrasal_count > 0:
        phrasal_instruction = f"\n- Exactly {phrasal_count} of them MUST be phrasal verbs (is_phrasal_verb: true)"

    difficulty = {
        "A1": "very basic beginner vocabulary (colors, numbers, family, body, food, animals, greetings, basic verbs like eat/drink/sleep)",
        "A2": "elementary vocabulary (daily routines, travel, shopping, weather, simple adjectives, common adverbs)",
        "B1": "intermediate vocabulary (work, education, opinions, feelings, health, media, abstract nouns)",
        "B2": "upper-intermediate vocabulary (business, politics, science, formal verbs, nuanced adjectives)",
        "C1": "advanced vocabulary (academic, literary, idiomatic, specialized, subtle distinctions)",
        "C2": "proficiency-level vocabulary (rare, literary, archaic, highly specialized, near-native)",
    }

    prompt = f"""Generate exactly {BATCH_SIZE} English vocabulary items for a Spanish speaker learning English.

Level: {level} ({difficulty[level]})
Batch: {batch_num}/{total_batches} (ensure NO duplicates with other batches - vary topics widely)
{phrasal_instruction}

For EACH word provide:
- english_word: the English word or phrase
- spanish_translation: primary Spanish translation
- spanish_alts: comma-separated alternative translations (2-3 alternatives, empty string if none)
- is_phrasal_verb: true/false
- part_of_speech: "verb", "noun", "adjective", "adverb", "phrase", "conjunction", "preposition"
- category: one of "general", "business", "academic", "travel", "daily_life", "emotions", "science", "technology", "health", "nature", "social"
- frequency_rank: approximate rank in English word frequency (1=most common, 10000=very rare)
- example_en: one clear example sentence in English (under 12 words)
- example_en_2: second clear example sentence in English (under 12 words)
- example_en_3: third clear example sentence in English (under 12 words)

Rules:
- Translations must be accurate and natural
- No duplicates within this batch
- All example sentences should be practical, clear, and use the word naturally
- The three examples should use different contexts when possible
- Part of speech must match the word usage in the example

Respond ONLY with a valid JSON array:
[{{"english_word":"...","spanish_translation":"...","spanish_alts":"...","is_phrasal_verb":false,"part_of_speech":"verb","category":"general","frequency_rank":500,"example_en":"...","example_en_2":"...","example_en_3":"..."}}]"""

    payload = json.dumps({
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.9,
            "responseMimeType": "application/json",
            "thinkingConfig": {"thinkingBudget": 0}
        }
    }).encode("utf-8")

    req = urllib.request.Request(GEMINI_URL, data=payload, headers={"Content-Type": "application/json"})
    
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            text = data["candidates"][0]["content"]["parts"][0]["text"]
            words = json.loads(text)
            if isinstance(words, list):
                return words
    except Exception as e:
        print(f"  ⚠ API error: {e}")
    
    return []

def insert_words(conn, words, level, limit=None):
    inserted = 0
    for w in words:
        if limit is not None and inserted >= limit:
            break
        try:
            english = w.get("english_word", "").strip()
            spanish = w.get("spanish_translation", "").strip()
            if not english or not spanish:
                continue

            cursor = conn.execute("""
                INSERT OR IGNORE INTO words 
                (english, spanish, spanish_alts, cefr_level, is_phrasal_verb, 
                 part_of_speech, category, frequency_rank, example_en, example_en_2, example_en_3)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                english.lower(),
                spanish.lower(),
                w.get("spanish_alts", "").lower(),
                level,
                1 if w.get("is_phrasal_verb") else 0,
                w.get("part_of_speech", "").lower(),
                w.get("category", "general").lower(),
                int(w.get("frequency_rank", 0)),
                w.get("example_en", ""),
                w.get("example_en_2", ""),
                w.get("example_en_3", ""),
            ))
            if cursor.rowcount > 0:
                inserted += 1
        except Exception:
            continue
    
    conn.commit()
    return inserted

def main():
    if not API_KEY:
        print("❌ Set GEMINI_API_KEY environment variable")
        sys.exit(1)

    print("🚀 Generating RoadWords vocabulary database...")
    conn = create_db()
    
    # Check existing
    existing = conn.execute("SELECT COUNT(*) FROM words").fetchone()[0]
    print(f"📊 Existing words: {existing}")

    total_generated = 0

    for level, target_count in LEVEL_COUNTS.items():
        # Check how many we already have for this level
        current = conn.execute("SELECT COUNT(*) FROM words WHERE cefr_level = ?", (level,)).fetchone()[0]
        remaining = target_count - current
        
        if remaining <= 0:
            print(f"✅ {level}: already have {current}/{target_count}")
            continue

        # Phrasal verb ratio by level
        phrasal_ratio = {"A1": 0.0, "A2": 0.05, "B1": 0.2, "B2": 0.3, "C1": 0.25, "C2": 0.15}[level]
        
        batches_attempted = 0
        while remaining > 0:
            batches_attempted += 1
            print(f"  🔄 Batch {batches_attempted} (need {remaining} more)...", end=" ", flush=True)
            words = generate_batch(level, batches_attempted, 50, phrasal_ratio)
            
            if words:
                before = conn.execute("SELECT COUNT(*) FROM words").fetchone()[0]
                
                # Recalculate remaining exactly for this batch limit
                current_now = conn.execute("SELECT COUNT(*) FROM words WHERE cefr_level = ?", (level,)).fetchone()[0]
                rem_now = target_count - current_now
                
                insert_words(conn, words, level, limit=rem_now)
                after = conn.execute("SELECT COUNT(*) FROM words").fetchone()[0]
                added = after - before
                total_generated += added
                print(f"✅ +{added} words (total: {after})")
                
                remaining = target_count - after
            else:
                print("❌ failed or empty batch returned")
                time.sleep(2)
            
            time.sleep(1.5)  # Rate limiting
            
            if batches_attempted >= 20:
                print(f"⚠️ Max batch attempts reached for {level}. Moving on.")
                break

    # Final stats
    print("\n" + "="*50)
    print("📊 FINAL DATABASE STATS")
    print("="*50)
    total = conn.execute("SELECT COUNT(*) FROM words").fetchone()[0]
    print(f"Total words: {total}")
    for level in LEVEL_COUNTS:
        count = conn.execute("SELECT COUNT(*) FROM words WHERE cefr_level = ?", (level,)).fetchone()[0]
        pv = conn.execute("SELECT COUNT(*) FROM words WHERE cefr_level = ? AND is_phrasal_verb = 1", (level,)).fetchone()[0]
        print(f"  {level}: {count} words ({pv} phrasal verbs)")
    
    print(f"\n✅ Database saved to: {DB_PATH}")
    conn.close()

if __name__ == "__main__":
    main()
