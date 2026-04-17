package com.example.regresoacasa.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false
    private var lastSpokenText: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "MX"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "Lenguaje no soportado o datos faltantes")
                // Intentar español genérico
                tts?.setLanguage(Locale("es"))
            }
            isInitialized = true
        } else {
            Log.e("TtsManager", "Error al inicializar TTS")
        }
    }

    fun speak(text: String, override: Boolean = false) {
        if (!isInitialized) {
            Log.w("TtsManager", "TTS no inicializado aún")
            return
        }

        if (!override && text == lastSpokenText) {
            return // No repetir lo mismo inmediatamente
        }

        lastSpokenText = text
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NavInstruction")
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
