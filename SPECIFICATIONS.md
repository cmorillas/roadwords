# RoadWords — English Learning App Technical Specification

> **Version:** 0.5.0  
> **Last updated:** 2026-04-03  
> **Platform:** Native Android (Kotlin + Jetpack Compose)  
> **Philosophy:** Offline-first, no network dependencies in production  

---

## 1. Overview

RoadWords is an Android application for Spanish speakers learning English vocabulary while driving. English is the target language. Spanish is used only as the learner's native-language support for prompts, translations, and answer checking.

The user listens to a word, responds orally with the requested translation, and then receives English context reinforcement. The system evaluates the response, manages progress, and repeats words according to a simplified spaced repetition algorithm.

### 1.1 Design Principles

1. **100% Offline** — Does not rely on an internet connection to function. Voice recognition uses the Google engine installed on the device (which works offline if the model is downloaded, or uses the cloud if there is internet).
2. **Hands-free** — Designed for driving. Everything works by voice. The UI is for visual feedback only.
3. **Simple and predictable** — The learning algorithm is transparent and its rules can be explained in a single sentence.
4. **Synonyms** — Accepts multiple valid translations without the need for an LLM.
5. **English-first context** — Context sentences are stored and spoken in English. Spanish context sentences are intentionally not part of the learning material.

---

## 2. Architecture

```
┌─────────────────────────────────────────────┐
│                  MainActivity               │
│               (Jetpack Compose)             │
├─────────────────────────────────────────────┤
│                MainViewModel                │
│         (MVVM, StateFlow, Coroutines)       │
├──────────┬──────────┬───────────┬───────────┤
│ TTSManager│ MicManager│ SoundManager│ SrsEngine │
│ (Local TTS)│(SpeechRec)│(ToneGen)   │(Algorithm)│
├──────────┴──────────┴───────────┴───────────┤
│              WordRepository                 │
│         (Active Round Management)           │
├─────────────────────────────────────────────┤
│         Room Database (SQLite)              │
│    WordEntity │ ProgressEntity │ WordDao    │
└─────────────────────────────────────────────┘
```

### 2.1 Technology Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Persistence | Room (SQLite) |
| Async | Kotlin Coroutines + StateFlow |
| TTS | Android TextToSpeech (local) |
| STT | Android SpeechRecognizer (Google) |
| Audio feedback | SoundPool + local OGG files |
| Build | Gradle 8.x + Android SDK 34 |

---

## 3. Learning Engine (SrsEngine)

### 3.1 Philosophy

SM-2 (SuperMemo) was discarded because:
- The `ease_factor` is calculated but not truly used in sessions without intervals of days.
- It's too complex to reason about its behavior.
- It adds no value in the context of short sessions in the car.

A **"hand of cards"** system was adopted, inspired by the original `VocabularyService.php` but simplified.

### 3.2 Engine Rules

```
"I have 6 cards in my hand. I ask them with weighted urgency. Words with more
remaining work appear more often, failed words come back soon after a short
cooldown, and recently seen words are spaced out when possible. When all are
consolidated, I shuffle a new hand."
```

#### Constants

| Parameter | Value | Justification |
|---|---|---|
| `ROUND_SIZE` | 6 | Enough to avoid boredom, few enough to consolidate. |
| `EN_TO_ES_THRESHOLD` | 3 | 3 correct understanding = consolidated. |
| `ES_TO_EN_THRESHOLD` | 2 | 2 correct producing = consolidated. |
| `REVIEW_CHANCE` | 10% | Probability of inserting an already learned word. |
| `FAILURE_COOLDOWN_TURNS` | 2 | A failed word returns soon, but not immediately. |
| `MIN_TURNS_BETWEEN_REPEATS` | 2 | Avoids echo-memory repeats when alternatives exist. |

#### Direction per word (weighted, not globally alternating)

The direction is determined **per word** based on its progress. Production is
not asked cold, but it also does not wait until recognition is fully complete.

```kotlin
fun getDirection(progress): String? {
    if (completely learned) -> 50/50 random review
    if (brand new or enToEsLevel == 0) -> "en_to_es"

    enWeight = max(0, 3 - enToEsLevel)
    esWeight = max(0, 2 - esToEnLevel) * (enToEsLevel / 3)

    return weightedRandom(enWeight, esWeight)
}
```

**Design decision:** Every word must get at least one EN→ES recognition hit before
ES→EN production can appear. After that, both directions compete by remaining
work. This keeps the "recognize before producing" principle without delaying
active recall until too late.

Example probabilities:

| Progress | Direction behavior |
|---|---|
| EN→ES 0/3, ES→EN 0/2 | 100% EN→ES |
| EN→ES 1/3, ES→EN 0/2 | Mostly EN→ES, ES→EN can appear |
| EN→ES 2/3, ES→EN 0/2 | ES→EN starts to become likely |
| EN→ES 3/3, ES→EN 0/2 | ES→EN until production is complete |

#### Response Evaluation

```kotlin
// On correct:
enToEsLevel++ (or esToEnLevel++)
streak++
lastFailedTurn cleared

// On fail:
level = max(0, level - 1)  // Drops 1 level, not to 0
streak = 0
lastFailedTurn = turn       // Short cooldown, then strong priority boost
```

#### Selection Priority

Within the active round, non-graduated words are selected with weighted urgency,
not pure FIFO and not pure randomness.

Hard filters:
1. Graduated words are excluded.
2. Failed words wait `FAILURE_COOLDOWN_TURNS` before returning, unless every
   remaining word is cooling down.
3. Words seen in the last `MIN_TURNS_BETWEEN_REPEATS` are avoided when there are
   other candidates.

Urgency score:

```text
deficit = max(0, 3 - enToEsLevel) + max(0, 2 - esToEnLevel)

urgency =
    deficit * 10
  + min(turnsSinceSeen, 5) * 2
  + failureBoost
```

Where `failureBoost = 15` after the failed word's cooldown has passed.

This means:
- New words appear often because their deficit is high.
- Almost-graduated words still appear, but less often.
- Failed words return soon after a small spacing gap.
- The order varies enough to avoid a mechanical loop.

#### Lifecycle of a Session Round

```
1. 6 fresh words (never seen) of the selected level are activated using the "Core & Spice" strategy:
   - 2 words from the top 33% most frequent (Core)
   - 2 words from the middle 34% frequent (Solid)
   - 1 word from the bottom 33% least frequent (Spice)
   - 1 random wildcard from any remaining available words
2. They are asked by weighted urgency.
3. Those that reach EN→ES ≥ 3 AND ES→EN ≥ 2 graduate and exit.
4. When all 6 graduate → automatic new round.
5. 10% chance to insert an already learned word for review.
```

If there are no fresh words left, unlearned words with the lowest progress are recycled.

---

## 4. Database

### 4.1 Room Schema (Runtime)

```kotlin
@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val english: String,
    val spanish: String,
    val englishAlts: String = "",     // "achieve,accomplish" 
    val spanishAlts: String = "",     // "lograr,conseguir"
    val cefrLevel: String = "B1",    // A1-C2
    val isPhrasalVerb: Boolean = false,
    val exampleEn: String = "",
    val exampleEn2: String = "",
    val exampleEn3: String = ""
)

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val wordId: Int,
    val enToEsLevel: Int = 0,        // 0→3 to graduate
    val esToEnLevel: Int = 0,        // 0→2 to graduate
    val streak: Int = 0,
    val totalReviews: Int = 0,
    val isInActiveRound: Boolean = false,
    val lastSeenTurn: Int = 0,       // Global turn when seen
    val lastFailedTurn: Int = -100   // Cooldown and failure boost tracking
)
```

### 4.2 Vocabulary Base (vocabulary.db)

Generated offline with Gemini API, packaged as an asset:

```sql
CREATE TABLE words (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    english TEXT NOT NULL UNIQUE,
    spanish TEXT NOT NULL,
    spanish_alts TEXT DEFAULT '',
    cefr_level TEXT NOT NULL,        -- A1, A2, B1, B2, C1, C2
    is_phrasal_verb INTEGER DEFAULT 0,
    part_of_speech TEXT DEFAULT '',   -- verb, noun, adjective...
    category TEXT DEFAULT '',         -- business, travel, academic...
    frequency_rank INTEGER DEFAULT 0, -- 1=most common
    example_en TEXT DEFAULT '',
    example_en_2 TEXT DEFAULT '',
    example_en_3 TEXT DEFAULT ''
);
```

Target distribution: ~1600 words (150 A1, 200 A2, 300 B1, 400 B2, 350 C1, 200 C2).

Each word carries up to three English context sentences. The session rotates
those English examples after correct answers and reads the selected example
aloud. Spanish context sentences are intentionally not stored because the app's
goal is learning English, not practicing Spanish.

`frequency_rank` is recalculated by `tools/enrich_vocabulary.py` using the
`wordfreq` English frequency list when available. It should be treated as an
approximate usefulness signal, especially for phrases and phrasal verbs, not as
a perfect corpus rank.

---

## 5. Services

### 5.1 TTSManager

- Engine: `android.speech.tts.TextToSpeech` (local, offline)
- Uses `CompletableDeferred` for `speakAndWait()` — suspends the coroutine until `onDone` triggers.
- Selects `Locale.US` for English, `Locale("es", "ES")` for Spanish.
- **Decision:** `speakAndWait()` is used instead of polling because polling with `delay(400)` caused a race condition where the mic opened before the TTS finished.

### 5.2 MicManager

- Engine: `android.speech.SpeechRecognizer` (Google, automatic online/offline)
- Uses `LANGUAGE_MODEL_FREE_FORM` because vocabulary answers are short phrases,
  not web searches.
- **Reuses the recognizer** between turns (pattern `ensureRecognizer()`) to avoid creation latency.
- Only destroys on `stopListening()` (end of session).
- Passes expected answers through `EXTRA_BIASING_STRINGS` so the recognizer can
  prefer vocabulary words like `succinct` over common false recognitions like `16th`.
- **Two separate flows:**
  - `transcript: StateFlow<String>` — real-time update (partials)
  - `finalResult: SharedFlow<List<String>>` — emitted only on `onResults` with
    up to 10 recognition candidates (triggers evaluation)
- **Auto-retry** on Error 7 (NO_MATCH) and Error 6 (SPEECH_TIMEOUT)
- **Decision:** `PREFER_OFFLINE` is not forced to respect the offline-first principle. Android decides automatically: cloud if there is a network, offline if not.

### 5.3 SoundManager

- Engine: `SoundPool` with local files in `res/raw`
- 3 sounds:
  - **Correct:** `correct.ogg` — soft two-note chime
  - **Incorrect:** `incorrect.ogg` — short low descending cue
  - **Learned!:** `learned.ogg` — short ascending arpeggio

### 5.4 StringEvaluator

- 100% offline evaluation using **Levenshtein distance**.
- Checks against ALL alternatives (`allSpanish()`, `allEnglish()`).
- Spanish translations are used for EN→ES answer checking, but contextual
  reinforcement after correct answers is English-only.
- Evaluates all final recognition candidates, accepting the first candidate that
  matches any valid answer. If none match, the closest candidate is used for
  feedback.
- Tolerance threshold: 1 character for short words (≤4), 2 for long words.
- **Decision:** Discarded using an LLM to evaluate because it breaks offline capability and adds latency.

---

## 6. Interface (UI)

### 6.1 Screens

| Screen | File | Function |
|---|---|---|
| Dashboard | `DashboardScreen.kt` | Level selector (A1-C2), start button, stats button |
| Session | `DriveSessionScreen.kt` | Word card, feedback, pulsating microphone |
| Stats | `StatsScreen.kt` | Summary (total/learned/pending), full list |

### 6.2 Theme

- Defined in `Theme.kt`
- Aesthetics: Dark mode with neon accents
- Main colors: `DeepSpace` (background), `NeonGreen` (correct), `AccentBlue` (info), `ErrorRed` (incorrect)
- CEFR colors: A1=light blue, A2=cyan, B1=green, B2=orange, C1=pink, C2=purple

### 6.3 Session Indicators

- **Header:** Round X/6 + Total learned/total
- **Card:** Direction (EN→ES / ES→EN), Frequency Rank (#number), PV badge, REVIEW (golden) badge
- **Word Progress:** EN→ES: X/3, ES→EN: X/2 (green when reached)
- **Feedback:** Green (correct), Red (incorrect), Golden (LEARNED!)
- **Microphone:** Pulsating ring based on voice volume (`onRmsChanged`)
- **Live transcript:** Blue text that updates while speaking

---

## 7. Session Flow

```
1. User selects levels (A1-C2) and taps "Start"
2. Active round of 6 fresh words from the level begins
3. LOOP:
   a. A non-graduated word is chosen by weighted urgency
   b. Determine direction (EN→ES first, then weighted by remaining work)
   c. TTS speaks the word (speakAndWait)
   d. Short 600ms silence buffer (to ignore speaker echo)
   e. Mic opens with expected-answer bias strings (listens for response)
   f. Partial transcription is shown in real time
   g. onResults → evaluateAnswer(finalCandidates)
   h. Levenshtein against all alternatives and all final candidates
   i. Sound + visual feedback (3 sec) or celebration (4 sec if LEARNED)
   j. Progress is updated in Room DB
   k. If all graduated → new round
   l. Goto 3a
4. User taps "End Trip" → mic destroyed, returns to Dashboard
```

---

## 8. Build and Deployment

### 8.1 Environment

```bash
# VPS with SDKMAN
# VPS Configuration
export JAVA_HOME=~/.sdkman/candidates/java/current
# Android SDK path in ~/Android/Sdk (via sdkmanager)
```

### 8.2 Build

```bash
cd ~/path/to/RoadWordsApp
JAVA_HOME=~/.sdkman/candidates/java/current ./gradlew assembleDebug
```

### 8.3 Publish

```bash
mkdir -p releases
cp app/build/outputs/apk/debug/app-debug.apk releases/RoadWords.apk
```

### 8.4 Local Server (for phone installation over HTTPS)

Run the following command to serve the downloaded `.apk` to your phone. Ensure you have your SSL certificate available:

```bash
cd releases
sudo bash -c "(trap 'kill 0' SIGINT; php -S 127.0.0.1:8000 & socat OPENSSL-LISTEN:443,reuseaddr,fork,cert=/path/to/your/certificate.pem,verify=0 TCP:127.0.0.1:8000)"
```

**Accessible at:** `https://[YOUR_DOMAIN_OR_IP]/RoadWords.apk`

### 8.5 Installation

The app has not been distributed yet, so Room starts at schema version 1 and no
migration path is maintained at this stage.

---

## 9. Key Technical Decisions

| Decision | Discarded Alternative | Reason |
|---|---|---|
| Per-word weighted direction | Global EN/ES alternation or strict 3-before-2 sequence | Starts with recognition but brings production earlier |
| `speakAndWait()` with `CompletableDeferred` | Polling with `delay()` | Race condition: mic heard the TTS |
| Reuse SpeechRecognizer | Destroy/create every turn | 500ms+ latency per creation |
| Bias SpeechRecognizer with expected answers | Blind recognition only | Reduces false recognitions like `succinct` → `16th` |
| Evaluate all recognition candidates | Use only top transcript | Avoids false negatives when the correct word is candidate 2-10 |
| Offline Levenshtein | LLM evaluation | Breaks offline, adds latency |
| 600ms post-TTS silence buffer | No buffer | Mic captured speaker echo |
| `SharedFlow` for finalResult | `StateFlow` for everything | Partial transcript triggered premature evaluation |
| Rounds of 6 | Rounds of 35 (original PHP) | In driving context, 35 is too much to consolidate |
| 3+2 Thresholds | 2+1 Thresholds (original PHP) | 3 correct is too few to effectively "learn" |
| Local sound files via SoundPool | DTMF tones | Better feedback quality while staying offline |
| No `PREFER_OFFLINE` | Force cloud | Respects offline-first philosophy |

---

## 10. File Structure

```
RoadWordsApp/
├── app/src/main/
│   ├── AndroidManifest.xml              # Permissions: RECORD_AUDIO, INTERNET
│   ├── assets/
│   │   └── vocabulary.db                # Pre-generated vocabulary DB
│   └── java/com/telytec/roadwords/
│       ├── MainActivity.kt              # Entry point, screen routing
│       ├── data/
│       │   ├── AppDatabase.kt           # Room DB config (version 1)
│       │   ├── WordEntity.kt            # Word Entity + allSpanish()/allEnglish()
│       │   ├── ProgressEntity.kt        # Per-word progress
│       │   ├── WordDao.kt               # SQL Queries (active round, stats)
│       │   ├── WordWithProgress.kt      # Join word+progress for stats
│       │   └── WordRepository.kt        # Round logic + seed vocabulary
│       ├── domain/
│       │   ├── SrsEngine.kt             # Learning engine (constants, apply)
│       │   └── StringEvaluator.kt       # Levenshtein + alternatives
│       ├── services/
│       │   ├── TTSManager.kt            # Text-to-Speech with speakAndWait
│       │   ├── MicManager.kt            # SpeechRecognizer + auto-retry
│       │   └── SoundManager.kt          # SoundPool feedback correct/incorrect/learned
│       └── ui/
│           ├── Theme.kt                 # Colors and Material 3 theme
│           ├── MainViewModel.kt         # MVVM: state, session, evaluation
│           ├── DashboardScreen.kt       # Home screen
│           ├── DriveSessionScreen.kt    # Driving session screen
│           └── StatsScreen.kt           # Statistics screen
├── tools/
│   └── generate_vocabulary.py           # Vocabulary builder script (Gemini)
├── build.gradle.kts                     # Project config
└── app/build.gradle.kts                 # Dependencies (Compose, Room, etc.)
```

---

## 11. Roadmap / Future Ideas

- [ ] **Import pre-generated vocabulary.db** instead of hardcoding in Kotlin.
- [ ] **Configurable round size** (5, 8, 12).
- [ ] **Configurable TTS speed**.
- [ ] **Advanced statistics:** Win rate by level, top 10 most failed, daily streak.
- [ ] **Level progress** visible in Dashboard (bars A1: 3/25, B2: 12/35...).
- [ ] **Whisper.cpp** as an offline alternative to Google's SpeechRecognizer.
- [ ] **Import JSON/CSV** from the mobile device without recompiling.
- [ ] **Example sentences** read aloud for context.
- [ ] **Review mode** — only already learned words, for maintenance.
