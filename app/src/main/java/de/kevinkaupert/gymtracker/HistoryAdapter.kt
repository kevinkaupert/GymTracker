package de.kevinkaupert.gymtracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class HistoryAdapter(
    private var sessions: List<WorkoutSession>,
    private val onEditSet: (WorkoutSet) -> Unit,
    private val onDeleteSet: (WorkoutSet) -> Unit,
    private val onDeleteSession: (WorkoutSession) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    // Store expanded state by date (unique) instead of position
    private val expandedDates = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sessionDate: TextView = view.findViewById(R.id.sessionDate)
        val setsContainer: LinearLayout = view.findViewById(R.id.setsContainer)
        val deleteSessionButton: ImageButton = view.findViewById(R.id.deleteSessionButton)
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
        holder.sessionDate.text = "Training: ${session.date}"
        
        val isExpanded = expandedDates.contains(session.date)
        holder.setsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.expandArrow.rotation = if (isExpanded) 180f else 0f

        // Clicking the header toggles expansion
        holder.sessionHeader.setOnClickListener {
            if (isExpanded) {
                expandedDates.remove(session.date)
            } else {
                expandedDates.add(session.date)
            }
            notifyItemChanged(position)
        }

        holder.deleteSessionButton.setOnClickListener {
            onDeleteSession(session)
        }

        holder.setsContainer.removeAllViews()
        if (isExpanded) {
            session.sets.forEach { set ->
                val setView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_history_set, holder.setsContainer, false)
                
                setView.findViewById<TextView>(R.id.exerciseName).text = set.exercise
                setView.findViewById<TextView>(R.id.setDetails).text = 
                    "Satz ${set.setNumber}: ${set.reps} x ${set.weight}kg (1RM: ${String.format(Locale.getDefault(), "%.1f", set.oneRm)})"
                
                setView.findViewById<ImageButton>(R.id.editSetButton).setOnClickListener {
                    onEditSet(set)
                }
                
                setView.findViewById<ImageButton>(R.id.deleteSetButton).setOnClickListener {
                    onDeleteSet(set)
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
}
