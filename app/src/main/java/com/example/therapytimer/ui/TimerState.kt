package com.example.therapytimer.ui

sealed class TimerState {
    object Idle : TimerState()
    data class Running(val remainingSeconds: Int) : TimerState()
    data class Completed(val count: Int) : TimerState()
}
