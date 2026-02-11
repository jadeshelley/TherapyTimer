package com.example.therapytimer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.therapytimer.data.Exercise
import com.example.therapytimer.data.ExerciseRoutine
import com.example.therapytimer.ui.TimerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimerViewModel : ViewModel() {
    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _currentCount = MutableStateFlow(0)
    val currentCount: StateFlow<Int> = _currentCount.asStateFlow()

    private val _currentExerciseIndex = MutableStateFlow(0)
    val currentExerciseIndex: StateFlow<Int> = _currentExerciseIndex.asStateFlow()

    private val _isBasicMode = MutableStateFlow(true)
    val isBasicMode: StateFlow<Boolean> = _isBasicMode.asStateFlow()

    private val _basicModeDuration = MutableStateFlow(30) // Default 30 seconds
    val basicModeDuration: StateFlow<Int> = _basicModeDuration.asStateFlow()

    private val _exerciseRoutine = MutableStateFlow<ExerciseRoutine?>(null)
    val exerciseRoutine: StateFlow<ExerciseRoutine?> = _exerciseRoutine.asStateFlow()

    /** In custom mode: indices of exercises that have been completed (all reps done). Used so chip selection doesn't mark earlier ones done. */
    private val _completedExerciseIndices = MutableStateFlow<Set<Int>>(emptySet())
    val completedExerciseIndices: StateFlow<Set<Int>> = _completedExerciseIndices.asStateFlow()

    private var countdownJob: Job? = null
    /** Called when a count completes; parameter is the count that just completed (for TTS). */
    var onTimerComplete: ((countJustCompleted: Int) -> Unit)? = null
    /** Called when there is 1 second left in the countdown (e.g. to stop voice listening before beep/TTS). */
    var onTimerOneSecondLeft: (() -> Unit)? = null
    /** Called when the entire routine is completed. fromTimerCompletion = true when the timer ran the last rep (sound will play after count); false when user tapped/said Done on last exercise (play sound now). */
    var onRoutineComplete: ((fromTimerCompletion: Boolean) -> Unit)? = null
    /** Called when the user starts the timer or resets (so UI can clear routine-complete sound guard). */
    var onRunStarted: (() -> Unit)? = null
    /** Called when we are about to start the countdown (button or voice). Play confirmation beep here. */
    var onStartOrNextConfirm: (() -> Unit)? = null

    fun setBasicMode(isBasic: Boolean) {
        _isBasicMode.value = isBasic
    }

    fun setBasicModeDuration(seconds: Int) {
        _basicModeDuration.value = seconds
    }

    fun setExerciseRoutine(routine: ExerciseRoutine) {
        val isSameRoutine = _exerciseRoutine.value == routine
        _exerciseRoutine.value = routine
        _isBasicMode.value = false
        // Only clear completed state when switching to a different routine (e.g. from Settings).
        // On rotation, MainActivity reapplies the same routine from preferences; don't clear then.
        if (!isSameRoutine) {
            _completedExerciseIndices.value = emptySet()
        }
    }

    fun startTimer() {
        if (_timerState.value is TimerState.Running) return
        if (!_isBasicMode.value && isRoutineComplete()) return
        onRunStarted?.invoke()

        val duration = if (_isBasicMode.value) {
            _basicModeDuration.value
        } else {
            val routine = _exerciseRoutine.value
            if (routine != null && _currentExerciseIndex.value < routine.exercises.size) {
                routine.exercises[_currentExerciseIndex.value].durationSeconds
            } else {
                _basicModeDuration.value
            }
        }

        onStartOrNextConfirm?.invoke()
        startCountdown(duration)
    }

    fun nextCount() {
        if (_timerState.value is TimerState.Completed) {
            // In custom mode, check if we've finished all reps for current exercise
            if (!_isBasicMode.value) {
                val routine = _exerciseRoutine.value
                val idx = _currentExerciseIndex.value
                if (routine != null && idx < routine.exercises.size) {
                    val needed = (routine.exercises[idx].repeats).coerceAtLeast(1)
                    if (_currentCount.value >= needed) {
                        _completedExerciseIndices.value = _completedExerciseIndices.value + idx
                        val next = getFirstUncompletedIndex()
                        if (next != null) {
                            _currentExerciseIndex.value = next
                            _currentCount.value = 0
                        }
                    }
                }
            }
            startTimer()
        }
    }

    fun restartCurrentCount() {
        countdownJob?.cancel()
        // If timer was completed, decrement count to restart at the same count number
        if (_timerState.value is TimerState.Completed && _currentCount.value > 0) {
            _currentCount.value--
        }
        _timerState.value = TimerState.Idle
        startTimer()
    }

    fun resetExercise() {
        countdownJob?.cancel()
        onRunStarted?.invoke()
        // Reset the *current* exercise only: keep the same exercise index,
        // but start its reps over from 0 and idle state.
        _currentCount.value = 0
        _timerState.value = TimerState.Idle
    }

    /** Used for the 'done' voice command in custom mode: mark current as done, go to leftmost uncompleted exercise (or routine complete), reset count, idle. */
    fun completeExerciseAndGoToNext() {
        countdownJob?.cancel()

        if (!_isBasicMode.value) {
            val routine = _exerciseRoutine.value
            val idx = _currentExerciseIndex.value
            if (routine != null && idx < routine.exercises.size) {
                _completedExerciseIndices.value = _completedExerciseIndices.value + idx
                val next = getFirstUncompletedIndex()
                if (next != null) {
                    _currentExerciseIndex.value = next
                    _currentCount.value = 0
                } else {
                    // Last exercise just marked done; routine is complete (user tapped/said Done)
                    _currentCount.value = 0
                    onRoutineComplete?.invoke(false)
                }
            }
        } else {
            _currentCount.value = 0
        }

        _timerState.value = TimerState.Idle
    }

    /** In custom mode: index of the first (leftmost) exercise not yet completed; null if all done. */
    private fun getFirstUncompletedIndex(): Int? {
        val routine = _exerciseRoutine.value ?: return null
        val completed = _completedExerciseIndices.value
        for (i in routine.exercises.indices) {
            if (i !in completed) return i
        }
        return null
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        _timerState.value = TimerState.Running(seconds)

        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _timerState.value = TimerState.Running(remaining)
                if (remaining == 1) {
                    onTimerOneSecondLeft?.invoke()
                }
            }
            // Increment count when timer completes
            _currentCount.value++
            val countJustCompleted = _currentCount.value
            // In custom mode, mark current done and go to first uncompleted when reps for current are done
            if (!_isBasicMode.value) {
                val routine = _exerciseRoutine.value
                val idx = _currentExerciseIndex.value
                if (routine != null && idx < routine.exercises.size) {
                    val needed = (routine.exercises[idx].repeats).coerceAtLeast(1)
                    if (_currentCount.value >= needed) {
                        _completedExerciseIndices.value = _completedExerciseIndices.value + idx
                        val next = getFirstUncompletedIndex()
                        if (next != null) {
                            _currentExerciseIndex.value = next
                            _currentCount.value = 0
                        }
                    }
                }
            }
            _timerState.value = TimerState.Completed(_currentCount.value)
            onTimerComplete?.invoke(countJustCompleted)
            if (isRoutineComplete()) {
                onRoutineComplete?.invoke(true)
            }
        }
    }

    /** Reorder exercises: move item at fromIndex to toIndex. Remaps completed indices and current index. Caller should persist routine after. */
    fun reorderExercises(fromIndex: Int, toIndex: Int) {
        if (_isBasicMode.value) return
        val routine = _exerciseRoutine.value ?: return
        val list = routine.exercises.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _exerciseRoutine.value = ExerciseRoutine(list)

        // Remap completed indices for the move
        fun remap(i: Int): Int = when {
            i == fromIndex -> toIndex
            fromIndex < toIndex && i in (fromIndex + 1)..toIndex -> i - 1
            fromIndex > toIndex && i in toIndex..<fromIndex -> i + 1
            else -> i
        }
        _completedExerciseIndices.value = _completedExerciseIndices.value.map { remap(it) }.toSet()
        _currentExerciseIndex.value = remap(_currentExerciseIndex.value)
    }

    /** Reset the entire routine: clear all progress, go to first exercise, idle. Custom mode only. */
    fun resetRoutine() {
        if (_isBasicMode.value) return
        countdownJob?.cancel()
        onRunStarted?.invoke()
        _completedExerciseIndices.value = emptySet()
        _currentExerciseIndex.value = 0
        _currentCount.value = 0
        _timerState.value = TimerState.Idle
    }

    /** Jump to a specific exercise (tap on chip). Starts that exercise from rep 0, idle so user can start. */
    fun goToExercise(index: Int) {
        if (_isBasicMode.value) return
        val routine = _exerciseRoutine.value ?: return
        countdownJob?.cancel()
        val safeIndex = index.coerceIn(0, (routine.exercises.size - 1).coerceAtLeast(0))
        _currentExerciseIndex.value = safeIndex
        _currentCount.value = 0
        _timerState.value = TimerState.Idle
    }

    fun getCurrentExerciseName(): String {
        return if (_isBasicMode.value) {
            "Exercise"
        } else {
            val routine = _exerciseRoutine.value
            if (routine != null && _currentExerciseIndex.value < routine.exercises.size) {
                routine.exercises[_currentExerciseIndex.value].name
            } else {
                "Exercise"
            }
        }
    }

    /** For custom mode: total repeats required for the current exercise. */
    fun getCurrentExerciseRepeats(): Int {
        if (_isBasicMode.value) return 1
        val routine = _exerciseRoutine.value ?: return 1
        val idx = _currentExerciseIndex.value
        if (idx >= routine.exercises.size) return 1
        return (routine.exercises[idx].repeats).coerceAtLeast(1)
    }

    /** True when in custom mode and all exercises have been completed. */
    fun isRoutineComplete(): Boolean {
        if (_isBasicMode.value) return false
        val routine = _exerciseRoutine.value ?: return false
        return _completedExerciseIndices.value.size >= routine.exercises.size
    }

    fun moveToNextExercise() {
        if (!_isBasicMode.value) {
            val routine = _exerciseRoutine.value
            if (routine != null && _currentExerciseIndex.value < routine.exercises.size - 1) {
                _currentExerciseIndex.value++
                _currentCount.value = 0
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
