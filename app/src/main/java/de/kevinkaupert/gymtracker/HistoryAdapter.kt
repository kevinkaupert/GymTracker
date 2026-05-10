package de.kevinkaupert.gymtracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var sessions: List<WorkoutSession>,
    private val onEditSet: (WorkoutSet) -> Unit,
    private val onDeleteSet: (WorkoutSet) -> Unit,
    private val onDeleteSession: (WorkoutSession) -> Unit,
    private val onExportSession: (WorkoutSession) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val expandedDates = mutableSetOf<String>()
    private val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val outputFormat = SimpleDateFormat("EEEE, d. MMM", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sessionDate: TextView = view.findViewById(R.id.sessionDate)
        val sessionSummary: TextView = view.findViewById(R.id.sessionSummary)
        val sessionNote: TextView = view.findViewById(R.id.sessionNote)
        val setsContainer: LinearLayout = view.findViewById(R.id.setsContainer)
        val exportSessionButton: ImageButton = view.findViewById(R.id.exportSessionButton)
        val expandArrow: View = view.findViewById(R.id.expandArrow)
        val sessionHeader: View = view.findViewById(R.id.sessionHeader)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        
        // Date formatting: 2024-03-20 -> Mittwoch, 20. Mär.
        val displayDate = try {
            val date = inputFormat.parse(session.date)
            if (date != null) outputFormat.format(date) else session.date
        } catch (e: Exception) {
            session.date
        }
        holder.sessionDate.text = displayDate

        val totalSets = session.sets.size
        val totalVolume = session.sets.sumOf { it.volume }
        val context = holder.itemView.context
        holder.sessionSummary.text = context.getString(R.string.session_summary_format, totalSets, totalVolume)

        if (session.note.isNotEmpty()) {
            holder.sessionNote.text = session.note
            holder.sessionNote.visibility = View.VISIBLE
        } else {
            holder.sessionNote.visibility = View.GONE
        }
        
        val isExpanded = expandedDates.contains(session.date)
        holder.setsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.expandArrow.rotation = if (isExpanded) 180f else 0f

        holder.sessionHeader.setOnClickListener {
            if (isExpanded) {
                expandedDates.remove(session.date)
            } else {
                expandedDates.add(session.date)
            }
            notifyItemChanged(position)
        }

        holder.exportSessionButton.setOnClickListener {
            onExportSession(session)
        }

        holder.setsContainer.removeAllViews()
        if (isExpanded) {
            // Add Header
            val headerView = LayoutInflater.from(holder.itemView.context)
                .inflate(R.layout.item_history_set_header, holder.setsContainer, false)
            holder.setsContainer.addView(headerView)

            session.sets.forEach { set ->
                val setView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_history_set, holder.setsContainer, false)
                
                setView.findViewById<TextView>(R.id.exerciseName).text = set.exercise
                
                // Show "s" for timed exercises
                val reps = if (set.isTimed) set.durationSeconds else set.reps
                val repsText = if (set.isTimed) "${reps}${context.getString(R.string.time_hint)}" else "${reps}"
                setView.findViewById<TextView>(R.id.repsValue).text = repsText
                
                val weight = set.weightKg ?: 0.0
                val weightDisplay = if (set.isBodyweight) context.getString(R.string.bodyweight_short) else String.format(Locale.getDefault(), "%.1f%s", weight, context.getString(R.string.unit_kg_short))
                setView.findViewById<TextView>(R.id.weightValue).text = weightDisplay
                
                setView.findViewById<TextView>(R.id.oneRmValue).text = 
                    String.format(Locale.getDefault(), "%.1f", set.oneRm)
                
                setView.findViewById<ImageButton>(R.id.editSetButton).setOnClickListener {
                    onEditSet(set)
                }
                
                holder.setsContainer.addView(setView)
            }
        }
    }

    override fun getItemCount() = sessions.size

    fun updateData(newSessions: List<WorkoutSession>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    fun getSessionAt(position: Int): WorkoutSession = sessions[position]
}
