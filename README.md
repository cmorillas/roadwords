# RoadWords 🚗📖

**RoadWords** is an Android application designed for Spanish speakers learning English vocabulary while driving. It is a **100% offline**, **hands-free**, and **voice-controlled** learning tool.

## 🌟 Key Features

- **Hands-Free Operation:** Designed specifically for use while driving. All interactions are via voice (STT and TTS).
- **Offline-First:** No internet dependency. Works anywhere, even in areas with poor coverage.
- **English Context Reinforcement:** Focuses on learning English through English context sentences.
- **Smart Learning Engine:** Uses a custom SRS (Spaced Repetition System) inspired by "hands of cards" for efficient consolidation.
- **CEFR-Level Support:** Vocabulary categorized from A1 to C2.
- **Rich Feedback:** Auditory cues and neon-accented dark mode UI for clear visual feedback.

## 🛠️ Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Database:** Room (SQLite) for progress, pre-packaged SQLite for vocabulary.
- **Concurrency:** Coroutines + StateFlow
- **Voice:** Android SpeechRecognizer + TextToSpeech
- **Architecture:** MVVM (Model-View-ViewModel)

## 📁 Project Structure

- `app/`: Main Android module.
  - `src/main/assets/`: Contains the `vocabulary.db` database.
  - `src/main/java/.../domain/`: SRS Engine and evaluation logic.
  - `src/main/java/.../services/`: TTS, Mic, and Sound management.
  - `src/main/java/.../ui/`: Jetpack Compose screens and ViewModels.
- `tools/`: Python scripts for vocabulary generation and enrichment using Gemini API.

## 🚀 Building the Project

### Prerequisites
- Android Studio Hedgehog or newer.
- Android SDK 34.
- Java 17+.

### Building from Command Line
```bash
./gradlew assembleDebug
```

## 🧠 Learning Philosophy
RoadWords avoids the complexity of SM-2 for short-term car sessions. Instead, it uses a **"Hand of Cards"** system:
1. Active round of 6 words.
2. Recognition (EN→ES) before Production (ES→EN).
3. Weighted urgency: words you struggle with or haven't seen in a while appear more frequently.
4. Automatic round advancement upon graduation (3 EN→ES + 2 ES→EN correct hits).

## 📄 License
*Specify license here (e.g., MIT, GPL...)*

---
*Developed by Cesar - Dedicated to safe and efficient learning on the road.*
