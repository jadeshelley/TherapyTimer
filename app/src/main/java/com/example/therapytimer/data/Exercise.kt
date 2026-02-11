package com.example.therapytimer.data

data class Exercise(
    val name: String,
    val durationSeconds: Int,
    val repeats: Int = 1  // How many times to do this exercise; 0 means "unspecified" (empty box in editor)
) {
    init {
        require(repeats >= 0) { "repeats must be at least 0" }
    }
}

data class ExerciseRoutine(
    val exercises: List<Exercise>
)

/** A named custom routine (one of possibly many). */
data class NamedRoutine(
    val id: String,
    val name: String,
    val exercises: List<Exercise>
) {
    fun toExerciseRoutine(): ExerciseRoutine = ExerciseRoutine(exercises)
}
