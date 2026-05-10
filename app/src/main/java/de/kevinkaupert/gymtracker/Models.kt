package de.kevinkaupert.gymtracker

data class WorkoutSet(
    val id: String, 
    val date: String,
    val time: String,
    val exercise: String,
    val setNumber: String,
    val reps: Int,
    val weight: Double,
    val oneRm: Double,
    val originFileName: String, // Verknüpfung zur Datei für absolut sicheres Löschen/Editieren
    val isSeconds: Boolean = false,
    val comment: String = ""
) {
    val volume: Double get() = reps * weight
}

data class WorkoutSession(
    val date: String,
    val fileName: String,
    val sets: List<WorkoutSet>,
    val comment: String = ""
)
