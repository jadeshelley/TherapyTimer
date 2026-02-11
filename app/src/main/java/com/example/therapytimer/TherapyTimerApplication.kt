package com.example.therapytimer

import android.app.Application
import android.util.Log

/**
 * Load JNA's native library from jniLibs (extracted at build time) before any Vosk/JNA code runs.
 * Required on Android: JNA cannot load from the JAR classpath, so we extract to jniLibs and load here.
 */
class TherapyTimerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("jnidispatch")
        } catch (e: UnsatisfiedLinkError) {
            Log.w("TherapyTimerApp", "JNA native lib not loaded; voice recognition will fail", e)
        }
    }
}
