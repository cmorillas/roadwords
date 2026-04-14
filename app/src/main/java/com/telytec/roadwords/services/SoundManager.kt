package com.telytec.roadwords.services

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Plays audio feedback using real sound files from res/raw.
 * - correct.ogg: Soft two-note chime
 * - incorrect.ogg: Short low descending cue
 * - learned.ogg: Short ascending arpeggio
 */
class SoundManager(context: Context) {
    private val soundPool: SoundPool
    private val correctId: Int
    private val incorrectId: Int
    private val learnedId: Int

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        correctId = soundPool.load(context, com.telytec.roadwords.R.raw.correct, 1)
        incorrectId = soundPool.load(context, com.telytec.roadwords.R.raw.incorrect, 1)
        learnedId = soundPool.load(context, com.telytec.roadwords.R.raw.learned, 1)
    }

    fun playCorrect() {
        soundPool.play(correctId, 0.55f, 0.55f, 1, 0, 1f)
    }

    fun playIncorrect() {
        soundPool.play(incorrectId, 0.45f, 0.45f, 1, 0, 1f)
    }

    fun playLearned() {
        soundPool.play(learnedId, 0.6f, 0.6f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
