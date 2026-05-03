package de.kevinkaupert.gymtracker

import java.util.UUID

enum class TrackingType {
    WEIGHT_REPS, BODYWEIGHT_REPS, DURATION, DISTANCE_TIME
}

data class ExerciseDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String, // push, pull, legs, core, grip, cardio
    val primaryMuscleGroups: List<String> = emptyList(),
    val secondaryMuscleGroups: List<String> = emptyList(),
    val equipment: String, // barbell, dumbbell, cable, machine, bodyweight
    val trackingType: TrackingType,
    val isBodyweight: Boolean,
    val defaultSets: Int? = null,
    val defaultReps: Int? = null,
    val notes: String? = null
)

data class WorkoutSet(
    val id: String = UUID.randomUUID().toString(), // Added for easier identification
    val setNumber: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceKm: Double? = null,
    val rpe: Int? = null,
    val notes: String? = null,
    // Calculated/Helper values for legacy support in UI
    val exercise: String = "",
    val date: String = "",
    val time: String = "",
    val oneRm: Double = 0.0,
    val isBodyweight: Boolean = false,
    val isTimed: Boolean = false,
    val originFileName: String = ""
) {
    val volume: Double
        get() = if (isTimed) (weightKg ?: 0.0) * (durationSeconds ?: 0)
                else (weightKg ?: 0.0) * (reps ?: 0)
}

data class ExerciseInstance(
    val exerciseId: String,
    val name: String, // Redundant but helpful for display/migration
    val category: String,
    val primaryMuscleGroups: List<String>,
    val secondaryMuscleGroups: List<String> = emptyList(),
    val equipment: String,
    val isBodyweight: Boolean,
    val trackingType: TrackingType,
    val sets: List<WorkoutSet>
)

data class WorkoutSession(
    val workoutId: String = UUID.randomUUID().toString(),
    val date: String, // YYYY-MM-DD
    val startedAt: String, // ISO-8601
    val endedAt: String? = null, // ISO-8601
    val type: String = "strength",
    val notes: String = "",
    val exercises: List<ExerciseInstance> = emptyList(),
    val fileName: String, // Keeping this for internal tracking if needed
    // Legacy support fields
    val sets: List<WorkoutSet> = emptyList(),
    val note: String = ""
)
