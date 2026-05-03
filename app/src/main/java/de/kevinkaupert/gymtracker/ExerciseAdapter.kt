package de.kevinkaupert.gymtracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip

class ExerciseAdapter(
    private var exercises: List<ExerciseDefinition>,
    private val onEdit: (ExerciseDefinition) -> Unit,
    private val onDelete: (ExerciseDefinition) -> Unit
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    fun updateData(newExercises: List<ExerciseDefinition>) {
        exercises = newExercises
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        holder.bind(exercise)
        holder.itemView.setOnClickListener { onEdit(exercise) }
        holder.itemView.setOnLongClickListener {
            onDelete(exercise)
            true
        }
    }

    override fun getItemCount(): Int = exercises.size

    class ExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.exerciseName)
        private val categoryChip: Chip = itemView.findViewById(R.id.categoryChip)
        private val muscleGroupsText: TextView = itemView.findViewById(R.id.muscleGroups)
        private val equipmentText: TextView = itemView.findViewById(R.id.equipmentInfo)
        private val trackingTypeText: TextView = itemView.findViewById(R.id.trackingTypeInfo)

        fun bind(exercise: ExerciseDefinition) {
            nameText.text = exercise.name
            categoryChip.text = exercise.category
            
            val primary = exercise.primaryMuscleGroups.joinToString(", ")
            val secondary = exercise.secondaryMuscleGroups.joinToString(", ")
            
            muscleGroupsText.text = when {
                primary.isNotEmpty() && secondary.isNotEmpty() -> "P: $primary | S: $secondary"
                primary.isNotEmpty() -> primary
                secondary.isNotEmpty() -> "S: $secondary"
                else -> ""
            }

            equipmentText.text = exercise.equipment
            trackingTypeText.text = exercise.trackingType.name
        }
    }
}
