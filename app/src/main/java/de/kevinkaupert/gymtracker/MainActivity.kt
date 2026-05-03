package de.kevinkaupert.gymtracker

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import android.provider.DocumentsContract
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import android.view.ViewGroup
import android.widget.ImageButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var exerciseDropdown: AutoCompleteTextView
    private lateinit var setNumberInput: TextInputEditText
    private lateinit var repsInput: TextInputEditText
    private lateinit var weightInput: TextInputEditText
    private lateinit var bodyweightCheckbox: MaterialCheckBox
    private lateinit var saveButton: MaterialButton
    private lateinit var finishButton: MaterialButton
    private lateinit var oneRmResult: TextView
    private lateinit var trainingRanges: TextView
    private lateinit var progressionGraph: ProgressionGraphView
    private lateinit var graphTitle: TextView
    private lateinit var timedCheckbox: MaterialCheckBox
    private lateinit var repsInputLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var workoutNoteInputLarge: TextInputEditText
    private lateinit var analyzeExerciseDropdown: AutoCompleteTextView
    private lateinit var analyzeModeDropdown: AutoCompleteTextView
    private lateinit var sessionStatus: TextView
    private lateinit var settingsExportAll: MaterialButton
    private lateinit var settingsExportToday: MaterialButton
    private lateinit var settingsMigrateAll: MaterialButton
    private lateinit var settingsGoogleDriveBackup: MaterialButton
    private lateinit var settingsRestoreBackup: MaterialButton
    private lateinit var settingsExportGit: MaterialButton
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var exerciseAdapter: ExerciseAdapter
    
    // Screens
    private lateinit var inputScreen: View
    private lateinit var historyScreen: View
    private lateinit var analyzeScreen: View
    private lateinit var settingsScreen: View
    private lateinit var exercisesScreen: View
    private lateinit var exerciseRecyclerView: RecyclerView
    private lateinit var addExerciseButton: MaterialButton
    private lateinit var syncExercisesButton: MaterialButton
    private lateinit var exerciseSearchEditText: TextInputEditText

    private var lastRefOneRm: Double = 0.0
    private var lastRefWeight: Double = 0.0
    private var lastRefIsBodyweight: Boolean = false
    private var lastRefIsTimed: Boolean = false

    private var graphMode = ProgressionGraphView.GraphMode.ONE_RM
    private var exerciseMasterList = mutableListOf<ExerciseDefinition>()

    private fun getTodayFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "workout_$date.json"
    }

    private fun calculate1RM(weight: Double, reps: Int, isBodyweight: Boolean, isTimed: Boolean): Double {
        return if (isTimed) {
            if (weight > 0) weight * (1 + reps / 30.0) else reps.toDouble()
        } else if (isBodyweight) {
            reps.toDouble()
        } else {
            weight * (1 + reps / 30.0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Restore state
        var restoredSelection = R.id.nav_input
        savedInstanceState?.let {
            val modeName = it.getString("graph_mode")
            if (modeName != null) {
                graphMode = ProgressionGraphView.GraphMode.valueOf(modeName)
            }
            restoredSelection = it.getInt("selected_item_id", R.id.nav_input)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.contentFrame)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Init UI
        exerciseDropdown = findViewById(R.id.exerciseDropdown)
        setNumberInput = findViewById(R.id.setNumberInput)
        repsInput = findViewById(R.id.repsInput)
        weightInput = findViewById(R.id.weightInput)
        bodyweightCheckbox = findViewById(R.id.bodyweightCheckbox)
        saveButton = findViewById(R.id.saveButton)
        finishButton = findViewById(R.id.finishButton)
        oneRmResult = findViewById(R.id.oneRmResult)
        trainingRanges = findViewById(R.id.trainingRanges)
        
        analyzeExerciseDropdown = findViewById(R.id.analyzeExerciseDropdown)
        analyzeModeDropdown = findViewById(R.id.analyzeModeDropdown)
        progressionGraph = findViewById(R.id.progressionGraph)
        graphTitle = findViewById(R.id.graphTitle)
        sessionStatus = findViewById(R.id.sessionStatus)

        settingsExportAll = findViewById(R.id.settingsExportAll)
        settingsExportToday = findViewById(R.id.settingsExportToday)
        settingsMigrateAll = findViewById(R.id.settingsMigrateAll)
        settingsGoogleDriveBackup = findViewById(R.id.settingsGoogleDriveBackup)
        settingsRestoreBackup = findViewById(R.id.settingsRestoreBackup)
        settingsExportGit = findViewById(R.id.settingsExportGit)
        
        bottomNavigation = findViewById(R.id.bottomNavigation)
        inputScreen = findViewById(R.id.inputScreen)
        historyScreen = findViewById(R.id.historyScreen)
        analyzeScreen = findViewById(R.id.analyzeScreen)
        settingsScreen = findViewById(R.id.settingsScreen)
        exercisesScreen = findViewById(R.id.exercisesScreen)
        exerciseRecyclerView = findViewById(R.id.exerciseRecyclerView)
        addExerciseButton = findViewById(R.id.addExerciseButton)
        syncExercisesButton = findViewById(R.id.syncExercisesButton)
        exerciseSearchEditText = findViewById(R.id.exerciseSearchEditText)
        historyRecyclerView = findViewById(R.id.historyRecyclerView)
        
        timedCheckbox = findViewById(R.id.timedCheckbox)
        repsInputLayout = findViewById(R.id.repsInputLayout)
        workoutNoteInputLarge = findViewById(R.id.workoutNoteInputLarge)

        workoutNoteInputLarge.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // We only update internal files when saving a set to avoid heavy disk I/O on every keystroke.
                // But for cross-set synchronization in the UI, this is where it happens.
            }
        })

        setupNavigation()
        setupHistory()
        setupExercises()
        setupAnalyzeScreen()
        setupSettingsScreen()
        updateExerciseAdapters()

        // WICHTIG: Setze die Auswahl UND aktualisiere die Sichtbarkeit manuell.
        // Das System stellt die ID zwar oft selbst wieder her, triggert aber den Listener nicht.
        bottomNavigation.selectedItemId = restoredSelection
        applyNavigation(restoredSelection)

        exerciseDropdown.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val exercise = s.toString()
                updateSetNumberForExercise(exercise)
                loadLastExerciseData(exercise)
                // Synchronize Analyze Screen with Current Exercise only if it's not being cleared
                if (exercise.isNotBlank()) {
                    analyzeExerciseDropdown.setText(exercise, false)
                    refreshAnalyzeView()
                }
            }
        })

        saveButton.setOnClickListener { saveEntry() }
        finishButton.setOnClickListener { finishWorkout() }

        bodyweightCheckbox.setOnCheckedChangeListener { _, isChecked ->
            weightInput.isEnabled = !isChecked
            if (isChecked) {
                weightInput.setText("0")
            }
        }

        timedCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                repsInputLayout.hint = getString(R.string.time_hint)
            } else {
                repsInputLayout.hint = getString(R.string.reps_hint)
            }
        }

        loadWorkoutNote()
        updateSessionStatus()
    }

    private fun loadWorkoutNote() {
        val file = File(filesDir, getTodayFileName())
        val (note, _) = readWorkoutFile(file)
        workoutNoteInputLarge.setText(note)
    }

    private fun setupNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            applyNavigation(item.itemId)
            true
        }
    }

    private fun applyNavigation(itemId: Int) {
        inputScreen.visibility = View.GONE
        historyScreen.visibility = View.GONE
        analyzeScreen.visibility = View.GONE
        settingsScreen.visibility = View.GONE
        exercisesScreen.visibility = View.GONE
        when(itemId) {
            R.id.nav_input -> {
                inputScreen.visibility = View.VISIBLE
                loadWorkoutNote()
            }
            R.id.nav_exercises -> {
                exercisesScreen.visibility = View.VISIBLE
                loadExercises()
            }
            R.id.nav_history -> {
                historyScreen.visibility = View.VISIBLE
                loadHistory()
            }
            R.id.nav_analyze -> {
                analyzeScreen.visibility = View.VISIBLE
                refreshAnalyzeView()
            }
            R.id.nav_settings -> settingsScreen.visibility = View.VISIBLE
            else -> {}
        }
    }

    private fun setupHistory() {
        historyAdapter = HistoryAdapter(emptyList(), 
            onEditSet = { editSet(it) }, 
            onDeleteSet = { confirmDeleteSet(it) }, 
            onDeleteSession = { confirmDeleteSession(it) },
            onExportSession = { exportSession(it) }
        )
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val session = historyAdapter.getSessionAt(position)
                confirmDeleteSession(session)
                historyAdapter.notifyItemChanged(position)
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val paint = Paint()
                    val icon: Drawable? = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_delete)
                    
                    if (dX < 0) { // Swiping to the left
                        paint.color = ContextCompat.getColor(this@MainActivity, R.color.error)
                        val background = RectF(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                        c.drawRect(background, paint)

                        icon?.let {
                            val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                            val iconTop = itemView.top + (itemView.height - it.intrinsicHeight) / 2
                            val iconBottom = iconTop + it.intrinsicHeight
                            val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                            val iconRight = itemView.right - iconMargin
                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            it.draw(c)
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(historyRecyclerView)
    }

    private fun setupExercises() {
        exerciseAdapter = ExerciseAdapter(emptyList(),
            onEdit = { showAddExerciseDialog(it) },
            onDelete = { confirmDeleteExercise(it) }
        )
        exerciseRecyclerView.layoutManager = LinearLayoutManager(this)
        exerciseRecyclerView.adapter = exerciseAdapter
        addExerciseButton.setOnClickListener { showAddExerciseDialog() }
        syncExercisesButton.setOnClickListener { syncExercisesFromHistory() }
        
        loadExercises()

        exerciseSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterExercises(s.toString())
            }
        })
    }

    private fun filterExercises(query: String) {
        val filtered = if (query.isEmpty()) {
            exerciseMasterList
        } else {
            exerciseMasterList.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true) ||
                it.primaryMuscleGroups.any { muscle -> muscle.contains(query, ignoreCase = true) } ||
                it.secondaryMuscleGroups.any { muscle -> muscle.contains(query, ignoreCase = true) }
            }
        }
        exerciseAdapter.updateData(filtered)
    }

    private fun loadExercises() {
        val file = File(filesDir, "exercises.json")
        if (!file.exists()) {
            exerciseMasterList = mutableListOf()
            return
        }
        try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<ExerciseDefinition>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(ExerciseDefinition(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    category = obj.getString("category"),
                    primaryMuscleGroups = obj.getJSONArray("primaryMuscleGroups").let { a -> List(a.length()) { a.getString(it) } },
                    secondaryMuscleGroups = obj.optJSONArray("secondaryMuscleGroups")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
                    equipment = obj.getString("equipment"),
                    trackingType = TrackingType.valueOf(obj.getString("trackingType")),
                    isBodyweight = obj.getBoolean("isBodyweight"),
                    notes = obj.optString("notes")
                ))
            }
            exerciseMasterList = list
            exerciseAdapter.updateData(list)
        } catch (e: Exception) {
            exerciseMasterList = mutableListOf()
        }
    }

    private fun saveExercises() {
        val file = File(filesDir, "exercises.json")
        val arr = JSONArray()
        exerciseMasterList.forEach { ex ->
            arr.put(JSONObject().apply {
                put("id", ex.id)
                put("name", ex.name)
                put("category", ex.category)
                put("primaryMuscleGroups", JSONArray(ex.primaryMuscleGroups))
                put("secondaryMuscleGroups", JSONArray(ex.secondaryMuscleGroups))
                put("equipment", ex.equipment)
                put("trackingType", ex.trackingType.name)
                put("isBodyweight", ex.isBodyweight)
                put("notes", ex.notes)
            })
        }
        file.writeText(arr.toString(4))
        updateExerciseAdapters()
    }

    private fun showAddExerciseDialog(existing: ExerciseDefinition? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_exercise, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.editExerciseName)
        val categoryInput = dialogView.findViewById<AutoCompleteTextView>(R.id.editExerciseCategory)
        
        val primaryChipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupPrimaryMuscles)
        val btnAddPrimary = dialogView.findViewById<Chip>(R.id.btnAddMuscleChip)
        val secondaryChipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupSecondaryMuscles)
        val btnAddSecondary = dialogView.findViewById<Chip>(R.id.btnAddSecondaryMuscleChip)
        
        val equipmentInput = dialogView.findViewById<AutoCompleteTextView>(R.id.editExerciseEquipment)
        val trackingTypeInput = dialogView.findViewById<AutoCompleteTextView>(R.id.editTrackingType)
        val bodyweightCheck = dialogView.findViewById<MaterialCheckBox>(R.id.editIsBodyweight)
        val notesInput = dialogView.findViewById<TextInputEditText>(R.id.editExerciseNotes)

        val selectedPrimary = mutableSetOf<String>()
        val selectedSecondary = mutableSetOf<String>()

        fun addChip(muscle: String, group: ChipGroup, selection: MutableSet<String>) {
            if (selection.contains(muscle)) return
            selection.add(muscle)
            val chip = Chip(this)
            chip.text = muscle
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener {
                group.removeView(chip)
                selection.remove(muscle)
            }
            group.addView(chip, group.childCount - 1)
        }

        fun showMuscleSelectionDialog(group: ChipGroup, selection: MutableSet<String>) {
            val standardMuscles = listOf(
                getString(R.string.muscle_chest), getString(R.string.muscle_back),
                getString(R.string.muscle_shoulders), getString(R.string.muscle_biceps),
                getString(R.string.muscle_triceps), getString(R.string.muscle_quads),
                getString(R.string.muscle_hamstrings), getString(R.string.muscle_calves),
                getString(R.string.muscle_abs), getString(R.string.muscle_glutes),
                getString(R.string.muscle_forearms), getString(R.string.muscle_traps),
                getString(R.string.muscle_lower_back)
            )
            
            val customMuscles = exerciseMasterList.flatMap { it.primaryMuscleGroups + it.secondaryMuscleGroups }
                .distinct()
                .filter { it !in standardMuscles }
                .toMutableList()

            val allOptions = (standardMuscles + customMuscles).toMutableList()
            val rv = RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                setPadding(0, 20, 0, 20)
            }

            var muscleDialog: androidx.appcompat.app.AlertDialog? = null

            class MuscleOptionAdapter(val options: MutableList<String>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val view = layoutInflater.inflate(R.layout.item_muscle_option, parent, false)
                    return object : RecyclerView.ViewHolder(view) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val muscle = options[position]
                    holder.itemView.findViewById<TextView>(R.id.muscleName).text = muscle
                    val deleteBtn = holder.itemView.findViewById<ImageButton>(R.id.btnDeleteMuscle)
                    val isCustom = muscle !in standardMuscles
                    deleteBtn.visibility = if (isCustom) View.VISIBLE else View.GONE
                    
                    deleteBtn.setOnClickListener {
                        options.removeAt(position)
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, options.size)
                    }

                    holder.itemView.setOnClickListener {
                        addChip(muscle, group, selection)
                        muscleDialog?.dismiss()
                    }
                }
                override fun getItemCount() = options.size
            }

            rv.adapter = MuscleOptionAdapter(allOptions)
            muscleDialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.primary_muscles_hint)
                .setView(rv)
                .setNeutralButton(R.string.add_custom_muscle) { _, _ ->
                    val customInput = TextInputEditText(this)
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.add_custom_muscle)
                        .setView(customInput)
                        .setPositiveButton(R.string.save_button) { _, _ ->
                            val custom = customInput.text.toString().trim()
                            if (custom.isNotEmpty()) addChip(custom, group, selection)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                .show()
        }

        btnAddPrimary.setOnClickListener { showMuscleSelectionDialog(primaryChipGroup, selectedPrimary) }
        btnAddSecondary.setOnClickListener { showMuscleSelectionDialog(secondaryChipGroup, selectedSecondary) }

        // Setup dropdowns
        val categories = listOf(getString(R.string.category_push), getString(R.string.category_pull), getString(R.string.category_legs), getString(R.string.category_core), getString(R.string.category_grip), getString(R.string.category_cardio))
        categoryInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories))

        val equipments = listOf(getString(R.string.equipment_barbell), getString(R.string.equipment_dumbbell), getString(R.string.equipment_cable), getString(R.string.equipment_machine), getString(R.string.equipment_bodyweight))
        equipmentInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, equipments))

        val trackingTypes = TrackingType.entries.map { it.name }
        trackingTypeInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, trackingTypes))

        existing?.let {
            nameInput.setText(it.name)
            categoryInput.setText(it.category, false)
            it.primaryMuscleGroups.forEach { muscle -> addChip(muscle, primaryChipGroup, selectedPrimary) }
            it.secondaryMuscleGroups.forEach { muscle -> addChip(muscle, secondaryChipGroup, selectedSecondary) }
            equipmentInput.setText(it.equipment, false)
            trackingTypeInput.setText(it.trackingType.name, false)
            bodyweightCheck.isChecked = it.isBodyweight
            notesInput.setText(it.notes)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.add_exercise_title else R.string.edit_set_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save_button, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isBlank()) {
                nameInput.error = getString(R.string.error_exercise_name_empty)
                return@setOnClickListener
            }
            val newEx = ExerciseDefinition(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name,
                category = categoryInput.text.toString(),
                primaryMuscleGroups = selectedPrimary.toList(),
                secondaryMuscleGroups = selectedSecondary.toList(),
                equipment = equipmentInput.text.toString(),
                trackingType = try { TrackingType.valueOf(trackingTypeInput.text.toString()) } catch(e: Exception) { TrackingType.WEIGHT_REPS },
                isBodyweight = bodyweightCheck.isChecked,
                notes = notesInput.text.toString()
            )
            if (existing == null) {
                exerciseMasterList.add(newEx)
            } else {
                val index = exerciseMasterList.indexOfFirst { it.id == existing.id }
                if (index != -1) exerciseMasterList[index] = newEx
            }
            saveExercises()
            loadExercises()
            Toast.makeText(this, R.string.success_exercise_saved, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    private fun confirmDeleteExercise(exercise: ExerciseDefinition) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_set_title) // Reuse strings where possible or add specific ones
            .setMessage(getString(R.string.delete_set_message))
            .setPositiveButton(R.string.delete) { _, _ ->
                exerciseMasterList.removeAll { it.id == exercise.id }
                saveExercises()
                loadExercises()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun syncExercisesFromHistory() {
        val recentNames = getRecentExercises()
        var addedCount = 0
        recentNames.forEach { name ->
            if (exerciseMasterList.none { it.name.equals(name, ignoreCase = true) }) {
                // Try to guess defaults based on history
                val isBW = isExerciseBodyweightInHistory(name)
                val isTimed = isExerciseTimedInHistory(name)
                
                exerciseMasterList.add(ExerciseDefinition(
                    name = name,
                    category = "Imported",
                    equipment = if (isBW) getString(R.string.equipment_bodyweight) else getString(R.string.equipment_machine),
                    trackingType = if (isTimed) TrackingType.DURATION else if (isBW) TrackingType.BODYWEIGHT_REPS else TrackingType.WEIGHT_REPS,
                    isBodyweight = isBW
                ))
                addedCount++
            }
        }
        if (addedCount > 0) {
            saveExercises()
            loadExercises()
            Toast.makeText(this, getString(R.string.sync_exercises_success, addedCount), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.sync_exercises_no_new, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isExerciseBodyweightInHistory(ex: String): Boolean {
        return filesDir.listFiles { _, name -> name.startsWith("workout_") }?.any { file ->
            val (_, arr) = readWorkoutFile(file)
            (0 until arr.length()).any { i ->
                val obj = arr.getJSONObject(i)
                obj.getString("übung").equals(ex, ignoreCase = true) && obj.optBoolean("isBodyweight", false)
            }
        } ?: false
    }

    private fun isExerciseTimedInHistory(ex: String): Boolean {
        return filesDir.listFiles { _, name -> name.startsWith("workout_") }?.any { file ->
            val (_, arr) = readWorkoutFile(file)
            (0 until arr.length()).any { i ->
                val obj = arr.getJSONObject(i)
                obj.getString("übung").equals(ex, ignoreCase = true) && 
                (obj.optBoolean("isTimed", false) || obj.optString("trackingType") == TrackingType.DURATION.name)
            }
        } ?: false
    }

    private fun setupAnalyzeScreen() {
        val modes = listOf(getString(R.string.mode_1rm), getString(R.string.mode_volume))
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modes)
        analyzeModeDropdown.setAdapter(modeAdapter)
        
        val currentModeText = if (graphMode == ProgressionGraphView.GraphMode.ONE_RM) modes[0] else modes[1]
        analyzeModeDropdown.setText(currentModeText, false)
        
        analyzeModeDropdown.setOnItemClickListener { _, _, position, _ ->
            graphMode = if (position == 0) ProgressionGraphView.GraphMode.ONE_RM else ProgressionGraphView.GraphMode.VOLUME
            refreshAnalyzeView()
        }

        analyzeExerciseDropdown.setOnItemClickListener { _, _, _, _ ->
            refreshAnalyzeView()
        }
    }

    private fun setupSettingsScreen() {
        settingsExportAll.setOnClickListener { exportAllData() }
        settingsExportToday.setOnClickListener { exportTodayOnly() }
        settingsMigrateAll.setOnClickListener { batchMigrateLegacyFiles() }
        settingsGoogleDriveBackup.setOnClickListener { backupToGoogleDrive() }
        settingsRestoreBackup.setOnClickListener { restoreFromBackup() }
        settingsExportGit.setOnClickListener { exportToFolder() }
    }

    private fun batchMigrateLegacyFiles() {
        val files = filesDir.listFiles { _, name -> name.startsWith("workout") && name.endsWith(".json") }
        var migratedCount = 0
        
        files?.forEach { file ->
            try {
                val jsonStr = file.readText().trim()
                if (jsonStr.startsWith("{")) {
                    val json = JSONObject(jsonStr)
                    if (!json.has("exercises")) {
                        val date = extractDateFromFilename(file.name)
                        val migrated = migrateLegacyToHierarchical(json, date)
                        file.writeText(migrated.toString(4))
                        migratedCount++
                    }
                } else if (jsonStr.startsWith("[")) {
                    val jsonArray = JSONArray(jsonStr)
                    val date = extractDateFromFilename(file.name)
                    val legacyObj = JSONObject().apply { put("sets", jsonArray) }
                    val migrated = migrateLegacyToHierarchical(legacyObj, date)
                    file.writeText(migrated.toString(4))
                    migratedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (migratedCount > 0) {
            Toast.makeText(this, getString(R.string.migrate_success, migratedCount), Toast.LENGTH_SHORT).show()
            loadHistory()
        } else {
            Toast.makeText(this, R.string.migrate_already_up_to_date, Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractDateFromFilename(filename: String): String {
        return filename.removePrefix("workout_").removePrefix("workout").removeSuffix(".json")
    }

    private fun refreshAnalyzeView() {
        val exercise = analyzeExerciseDropdown.text.toString()
        val modeLabel = if (graphMode == ProgressionGraphView.GraphMode.ONE_RM) getString(R.string.mode_1rm) else getString(R.string.mode_volume)
        graphTitle.text = getString(R.string.graph_title_format, getString(R.string.analyze_title), modeLabel, exercise)
        updateProgressionView(exercise)
        updateSessionStatus()
    }

    private fun loadHistory() {
        val sessions = mutableListOf<WorkoutSession>()
        val files = filesDir.listFiles { _, name -> name.startsWith("workout") }
        
        files?.sortedByDescending { it.name }?.forEach { file ->
            try {
                val (sessionNote, jsonArray) = readWorkoutFile(file)
                if (jsonArray.length() == 0 && sessionNote.isEmpty()) return@forEach
                
                val sets = mutableListOf<WorkoutSet>()
                val dateFromFilename = file.name.removePrefix("workout_").removeSuffix(".json")

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    
                    val exercise = obj.optString("übung", obj.optString("name", obj.optString("exercise", getString(R.string.unknown_exercise))))
                    val isTimed = obj.optBoolean("isTimed", obj.optString("trackingType") == TrackingType.DURATION.name)
                    val isBW = obj.optBoolean("isBodyweight", false)
                    
                    val reps = if (isTimed) null else {
                        val r = obj.optInt("reps", -1)
                        if (r != -1) r else obj.optInt("wiederholungen", 0)
                    }
                    val duration = if (isTimed) {
                        val d = obj.optInt("durationSeconds", -1)
                        if (d != -1) d else obj.optInt("wiederholungen", 0)
                    } else null
                    
                    val weight = if (obj.has("weightKg")) obj.getDouble("weightKg") else obj.optDouble("gewicht", 0.0)
                    
                    val oneRm = if (isTimed) {
                        if (weight > 0) weight * (1 + (duration ?: 0) / 30.0) else (duration ?: 0).toDouble()
                    } else if (isBW) {
                        (reps ?: 0).toDouble()
                    } else {
                        weight * (1 + (reps ?: 0) / 30.0)
                    }

                    sets.add(WorkoutSet(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        setNumber = obj.optInt("setNumber", obj.optInt("satz", i + 1)),
                        reps = reps,
                        weightKg = weight,
                        durationSeconds = duration,
                        notes = obj.optString("notes", obj.optString("note", "")),
                        exercise = exercise,
                        date = obj.optString("date", obj.optString("tag", dateFromFilename)),
                        time = obj.optString("time", obj.optString("uhrzeit", "--:--")),
                        oneRm = obj.optDouble("oneRm", oneRm),
                        isBodyweight = isBW,
                        isTimed = isTimed,
                        originFileName = file.name
                    ))
                }
                
                sessions.add(WorkoutSession(
                    date = dateFromFilename,
                    startedAt = dateFromFilename,
                    notes = sessionNote,
                    fileName = file.name,
                    sets = sets,
                    note = sessionNote
                ))
            } catch (e: Exception) {
                Log.e("GymTracker", "Error loading file ${file.name}", e)
            }
        }
        updateSessionStatus()
        historyAdapter.updateData(sessions)
    }

    private fun saveEntry() {
        val exercise = exerciseDropdown.text.toString()
        val setNumberStr = setNumberInput.text.toString()
        val repsStr = repsInput.text.toString()
        val weightStr = weightInput.text.toString()
        val isBodyweight = bodyweightCheckbox.isChecked

        if (exercise.isEmpty() || setNumberStr.isEmpty() || repsStr.isEmpty() || (weightStr.isEmpty() && !isBodyweight)) {
            Toast.makeText(this, R.string.fill_fields_error, Toast.LENGTH_SHORT).show()
            return
        }

        // Validation: Ensure exercise exists in Master Data or prompt to add it
        val exerciseDef = exerciseMasterList.find { it.name.equals(exercise, ignoreCase = true) }
        if (exerciseDef == null) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unknown_exercise)
                .setMessage(getString(R.string.error_exercise_not_in_master, exercise))
                .setPositiveButton(R.string.add_exercise_title) { _, _ -> 
                    // Open Master Data screen and show dialog
                    bottomNavigation.selectedItemId = R.id.nav_exercises
                    applyNavigation(R.id.nav_exercises)
                    showAddExerciseDialog(ExerciseDefinition(name = exercise, category = "", equipment = "", trackingType = TrackingType.WEIGHT_REPS, isBodyweight = false))
                }
                .setNegativeButton(R.string.save_button) { _, _ -> 
                    val reps = repsStr.toInt()
                    val isTimed = timedCheckbox.isChecked
                    val weight = if (isBodyweight && !isTimed) 0.0 else if (weightStr.isEmpty()) 0.0 else weightStr.toDouble()
                    val note = workoutNoteInputLarge.text.toString()
                    performSave(exercise, setNumberStr, reps, weight, isBodyweight, isTimed, note)
                }
                .setNeutralButton(R.string.cancel, null)
                .show()
            return
        }

        val reps = repsStr.toInt()
        val isTimed = timedCheckbox.isChecked
        val weight = if (isBodyweight && !isTimed) 0.0 else if (weightStr.isEmpty()) 0.0 else weightStr.toDouble()
        val note = workoutNoteInputLarge.text.toString()
        
        // Validation: Warn on extreme deviations (> 50%)
        // 1RM doesn't make sense for timed exercises in the same way, 
        // but we can still track progress via volume or just skip warning for timed.
        if (lastRefOneRm > 0 && !isTimed) {
            val currentOneRm = if (isBodyweight) reps.toDouble() else weight * (1 + reps / 30.0)
            val deviation = abs(currentOneRm - lastRefOneRm) / lastRefOneRm
            
            // If the BW status changed, a deviation is expected, but we still warn if it's huge
            if (deviation > 0.5) {
                val unit = if (isBodyweight) getString(R.string.unit_reps_bw) else getString(R.string.unit_kg_1rm)
                val refUnit = if (lastRefIsBodyweight) getString(R.string.unit_reps_bw) else getString(R.string.unit_kg_1rm)
                
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.deviation_title)
                    .setMessage(getString(R.string.deviation_message, currentOneRm, unit, lastRefOneRm, refUnit))
                    .setPositiveButton(R.string.save_button) { _, _ -> performSave(exercise, setNumberStr, reps, weight, isBodyweight, isTimed, note) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                return
            }
        }

        performSave(exercise, setNumberStr, reps, weight, isBodyweight, isTimed, note)
    }

    private fun performSave(exercise: String, setNumberStr: String, reps: Int, weight: Double, isBodyweight: Boolean, isTimed: Boolean, note: String) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        // Find exercise definition to get tracking type and other info
        val exerciseDef = exerciseMasterList.find { it.name.equals(exercise, ignoreCase = true) }
        val trackingType = exerciseDef?.trackingType ?: if (isTimed) TrackingType.DURATION else if (isBodyweight) TrackingType.BODYWEIGHT_REPS else TrackingType.WEIGHT_REPS

        val setObj = JSONObject().apply {
            put("setNumber", setNumberStr.toInt())
            if (trackingType == TrackingType.DURATION || trackingType == TrackingType.DISTANCE_TIME) {
                put("durationSeconds", reps)
            } else {
                put("reps", reps)
            }
            if (trackingType == TrackingType.WEIGHT_REPS || trackingType == TrackingType.DISTANCE_TIME || (!isBodyweight && trackingType == TrackingType.BODYWEIGHT_REPS)) {
                put("weightKg", weight)
            }
        }

        val file = File(filesDir, getTodayFileName())
        val sessionObj = if (file.exists()) {
            val content = file.readText().trim()
            try {
                if (content.startsWith("{")) {
                    val obj = JSONObject(content)
                    if (obj.has("exercises")) obj else migrateLegacyToHierarchical(obj, currentDate)
                } else if (content.startsWith("[")) {
                    val legacyArr = JSONArray(content)
                    val legacyObj = JSONObject().apply { put("sets", legacyArr) }
                    migrateLegacyToHierarchical(legacyObj, currentDate)
                } else {
                    createNewSession(currentDate, nowIso)
                }
            } catch (e: Exception) {
                createNewSession(currentDate, nowIso)
            }
        } else {
            createNewSession(currentDate, nowIso)
        }

        // Update session note if provided
        if (note.isNotEmpty()) sessionObj.put("notes", note)

        val exercisesArr = sessionObj.getJSONArray("exercises")
        var exerciseInst: JSONObject? = null
        for (i in 0 until exercisesArr.length()) {
            val ex = exercisesArr.getJSONObject(i)
            if (ex.getString("name").equals(exercise, ignoreCase = true)) {
                exerciseInst = ex
                break
            }
        }

        if (exerciseInst == null) {
            exerciseInst = JSONObject().apply {
                put("exerciseId", exerciseDef?.id ?: "")
                put("name", exercise)
                put("category", exerciseDef?.category ?: "")
                put("primaryMuscleGroups", JSONArray(exerciseDef?.primaryMuscleGroups ?: emptyList<String>()))
                put("secondaryMuscleGroups", JSONArray(exerciseDef?.secondaryMuscleGroups ?: emptyList<String>()))
                put("equipment", exerciseDef?.equipment ?: "")
                put("isBodyweight", isBodyweight)
                put("trackingType", trackingType.name)
                put("sets", JSONArray())
            }
            exercisesArr.put(exerciseInst)
        }

        exerciseInst!!.getJSONArray("sets").put(setObj)
        file.writeText(sessionObj.toString(4))

        // UI Update logic (legacy compatibility for now)
        val oneRm = if (isTimed) {
            if (weight > 0) weight * (1 + reps / 30.0) else reps.toDouble()
        } else if (isBodyweight) {
            reps.toDouble()
        } else {
            weight * (1 + reps / 30.0)
        }
        
        setNumberInput.setText((setNumberStr.toInt() + 1).toString())
        repsInput.text?.clear()
        
        if (isTimed) {
            if (weight > 0) {
                oneRmResult.text = getString(R.string.current_1rm, oneRm)
            } else {
                oneRmResult.text = getString(R.string.current_basis_wdh, oneRm) + " " + getString(R.string.time_hint)
            }
        } else {
            oneRmResult.text = if (isBodyweight) 
                getString(R.string.current_basis_wdh, oneRm)
            else 
                getString(R.string.current_1rm, oneRm)
        }
            
        updateTrainingRanges(oneRm, isBodyweight, isTimed, weight)
        updateSessionStatus()
        updateExerciseAdapters()
        Toast.makeText(this, R.string.saved_success, Toast.LENGTH_SHORT).show()
    }

    private fun createNewSession(date: String, startedAt: String): JSONObject {
        return JSONObject().apply {
            put("workoutId", UUID.randomUUID().toString())
            put("date", date)
            put("startedAt", startedAt)
            put("type", "strength")
            put("notes", "")
            put("exercises", JSONArray())
        }
    }

    private fun migrateLegacyToHierarchical(legacyObj: JSONObject, date: String): JSONObject {
        val newSession = createNewSession(date, date + "T00:00:00") // Approximation
        newSession.put("notes", legacyObj.optString("note", legacyObj.optString("notes", "")))
        
        val legacySets = if (legacyObj.has("sets")) {
            legacyObj.optJSONArray("sets") ?: JSONArray()
        } else if (legacyObj.has("übung") || legacyObj.has("exercise")) {
            // Single set object
            JSONArray().apply { put(legacyObj) }
        } else {
            JSONArray()
        }
        
        val exercisesMap = mutableMapOf<String, JSONObject>()

        for (i in 0 until legacySets.length()) {
            val s = legacySets.optJSONObject(i) ?: continue
            val exName = s.optString("übung", s.optString("exercise", "Unknown"))
            val exerciseInst = exercisesMap.getOrPut(exName) {
                // Try to find in master data
                val master = exerciseMasterList.find { it.name.equals(exName, ignoreCase = true) }
                JSONObject().apply {
                    put("exerciseId", master?.id ?: "")
                    put("name", exName)
                    put("category", master?.category ?: "")
                    put("primaryMuscleGroups", JSONArray(master?.primaryMuscleGroups ?: emptyList<String>()))
                    put("secondaryMuscleGroups", JSONArray(master?.secondaryMuscleGroups ?: emptyList<String>()))
                    put("equipment", master?.equipment ?: "")
                    put("isBodyweight", master?.isBodyweight ?: s.optBoolean("isBodyweight"))
                    put("trackingType", master?.trackingType?.name ?: if (s.optBoolean("isTimed")) TrackingType.DURATION.name else if (s.optBoolean("isBodyweight")) TrackingType.BODYWEIGHT_REPS.name else TrackingType.WEIGHT_REPS.name)
                    put("sets", JSONArray())
                }
            }
            val setObj = JSONObject().apply {
                put("setNumber", s.optInt("satz", s.optInt("setNumber", 1)))
                if (s.optBoolean("isTimed") || exerciseInst.getString("trackingType") == TrackingType.DURATION.name) {
                    put("durationSeconds", s.optInt("wiederholungen", s.optInt("reps", s.optInt("durationSeconds", 0))))
                } else {
                    put("reps", s.optInt("wiederholungen", s.optInt("reps", 0)))
                }
                put("weightKg", s.optDouble("gewicht", s.optDouble("weightKg", 0.0)))
                put("notes", s.optString("note", s.optString("notes", "")))
            }
            exerciseInst.getJSONArray("sets").put(setObj)
        }
        
        val exercisesArr = JSONArray()
        exercisesMap.values.forEach { exercisesArr.put(it) }
        newSession.put("exercises", exercisesArr)
        return newSession
    }

    private fun updateTodayNote(note: String) {
        val file = File(filesDir, getTodayFileName())
        val (_, sets) = readWorkoutFile(file)
        if (sets.length() > 0 || note.isNotEmpty()) {
            writeWorkoutFile(file, note, sets)
        }
    }

    private fun readWorkoutFile(file: File): Pair<String, JSONArray> {
        if (!file.exists() || file.length() == 0L) return Pair("", JSONArray())
        return try {
            val content = file.readText().trim()
            if (content.startsWith("{")) {
                val obj = JSONObject(content)
                if (obj.has("exercises")) {
                    // Hierarchical format - flatten for legacy reading/count
                    val allSets = JSONArray()
                    val exercises = obj.getJSONArray("exercises")
                    for (i in 0 until exercises.length()) {
                        val ex = exercises.getJSONObject(i)
                        val setsArr = ex.getJSONArray("sets")
                        for (j in 0 until setsArr.length()) {
                            val s = setsArr.getJSONObject(j)
                            val combined = JSONObject(s.toString())
                            combined.put("übung", ex.getString("name"))
                            combined.put("isBodyweight", ex.optBoolean("isBodyweight", false))
                            combined.put("trackingType", ex.optString("trackingType", TrackingType.WEIGHT_REPS.name))
                            combined.put("primaryMuscleGroups", ex.optJSONArray("primaryMuscleGroups"))
                            combined.put("secondaryMuscleGroups", ex.optJSONArray("secondaryMuscleGroups"))
                            allSets.put(combined)
                        }
                    }
                    Pair(obj.optString("notes", ""), allSets)
                } else if (obj.has("sets")) {
                    Pair(obj.optString("note", obj.optString("notes", "")), obj.optJSONArray("sets") ?: JSONArray())
                } else if (obj.has("übung") || obj.has("exercise") || obj.has("satz")) {
                    // Single set object at top level
                    Pair("", JSONArray().apply { put(obj) })
                } else {
                    Pair(obj.optString("note", obj.optString("notes", "")), JSONArray())
                }
            } else if (content.startsWith("[")) {
                val arr = JSONArray(content)
                val firstNote = if (arr.length() > 0) arr.optJSONObject(0)?.optString("note", "") ?: "" else ""
                Pair(firstNote, arr)
            } else {
                Pair("", JSONArray())
            }
        } catch (e: Exception) {
            Pair("", JSONArray())
        }
    }

    private fun writeWorkoutFile(file: File, note: String, sets: JSONArray) {
        try {
            val obj = JSONObject().apply {
                put("note", note)
                put("sets", sets)
            }
            file.writeText(obj.toString(4))
        } catch (e: Exception) {}
    }


    private fun exportSession(session: WorkoutSession) {
        val file = File(filesDir, session.fileName)
        if (file.exists()) {
            shareFile(file, "Workout from ${session.date}")
        }
    }

    private fun confirmDeleteSession(session: WorkoutSession) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_session_title)
            .setMessage(getString(R.string.delete_session_message, session.date))
            .setPositiveButton(R.string.delete) { _, _ -> deleteSession(session) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSession(session: WorkoutSession) {
        val file = File(filesDir, session.fileName)
        if (file.exists() && file.delete()) {
            loadHistory()
            updateSessionStatus()
            refreshAnalyzeView()
            Toast.makeText(this, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteSet(set: WorkoutSet) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_set_title)
            .setMessage(R.string.delete_set_message)
            .setPositiveButton(R.string.delete) { _, _ -> deleteSet(set) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSet(set: WorkoutSet) {
        val file = File(filesDir, set.originFileName)
        if (!file.exists()) return
        try {
            val jsonStr = file.readText()
            val json = JSONObject(jsonStr)
            
            if (json.has("exercises")) {
                val exercisesArr = json.getJSONArray("exercises")
                var removed = false
                for (i in 0 until exercisesArr.length()) {
                    val ex = exercisesArr.getJSONObject(i)
                    if (ex.getString("name").equals(set.exercise, ignoreCase = true)) {
                        val setsArr = ex.getJSONArray("sets")
                        for (j in 0 until setsArr.length()) {
                            val s = setsArr.getJSONObject(j)
                            if (s.getInt("setNumber") == set.setNumber) {
                                setsArr.remove(j)
                                removed = true
                                break
                            }
                        }
                        if (setsArr.length() == 0) {
                            exercisesArr.remove(i)
                        }
                    }
                    if (removed) break
                }
                if (exercisesArr.length() > 0 || json.optString("notes").isNotEmpty()) {
                    file.writeText(json.toString(4))
                } else {
                    file.delete()
                }
            } else {
                // Legacy delete
                val (note, jsonArray) = readWorkoutFile(file)
                val newArray = JSONArray()
                var found = false
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = if (obj.has("id")) obj.getString("id") else "${obj.optString("tag", "")}_${obj.optString("uhrzeit", "")}"
                    if (id != set.id) newArray.put(obj) else found = true
                }
                if (found) {
                    if (newArray.length() > 0 || note.isNotEmpty()) {
                        writeWorkoutFile(file, note, newArray)
                    } else {
                        file.delete()
                    }
                }
            }
            loadHistory()
            updateSessionStatus()
            refreshAnalyzeView()
            Toast.makeText(this, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun editSet(set: WorkoutSet) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_set, null)
        val editReps = dialogView.findViewById<TextInputEditText>(R.id.editReps)
        val editWeight = dialogView.findViewById<TextInputEditText>(R.id.editWeight)
        val editIsBodyweight = dialogView.findViewById<MaterialCheckBox>(R.id.editIsBodyweight)
        val editIsTimed = dialogView.findViewById<MaterialCheckBox>(R.id.editIsTimed)
        val editRepsLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.editRepsLayout)

        val currentReps = if (set.isTimed) set.durationSeconds else set.reps
        editReps.setText(currentReps?.toString() ?: "0")
        editWeight.setText((set.weightKg ?: 0.0).toString())
        editIsBodyweight.isChecked = set.isBodyweight
        editIsTimed.isChecked = set.isTimed
        editWeight.isEnabled = !set.isBodyweight
        
        if (set.isTimed) {
            editRepsLayout.hint = getString(R.string.time_hint)
        }

        editIsBodyweight.setOnCheckedChangeListener { _, isChecked ->
            editWeight.isEnabled = !isChecked
            if (isChecked) {
                editWeight.setText("0")
            }
        }

        editIsTimed.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                editRepsLayout.hint = getString(R.string.time_hint)
            } else {
                editRepsLayout.hint = getString(R.string.reps_hint)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_set_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save_button) { _, _ ->
                val r = editReps.text.toString().toIntOrNull() ?: currentReps ?: 0
                val isBW = editIsBodyweight.isChecked
                val isT = editIsTimed.isChecked
                val w = if (isBW && !isT) 0.0 else (editWeight.text.toString().toDoubleOrNull() ?: set.weightKg ?: 0.0)
                updateSetInFile(set, r, w, isBW, isT)
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun updateSetInFile(set: WorkoutSet, newReps: Int, newWeight: Double, isBodyweight: Boolean, isTimed: Boolean) {
        val file = File(filesDir, set.originFileName)
        try {
            val jsonStr = file.readText()
            val json = JSONObject(jsonStr)
            
            if (json.has("exercises")) {
                val exercisesArr = json.getJSONArray("exercises")
                var found = false
                for (i in 0 until exercisesArr.length()) {
                    val ex = exercisesArr.getJSONObject(i)
                    if (ex.getString("name").equals(set.exercise, ignoreCase = true)) {
                        val setsArr = ex.getJSONArray("sets")
                        for (j in 0 until setsArr.length()) {
                            val s = setsArr.getJSONObject(j)
                            if (s.getInt("setNumber") == set.setNumber) {
                                if (isTimed) {
                                    s.put("durationSeconds", newReps)
                                    s.remove("reps")
                                } else {
                                    s.put("reps", newReps)
                                    s.remove("durationSeconds")
                                }
                                s.put("weightKg", newWeight)
                                found = true
                                break
                            }
                        }
                    }
                    if (found) break
                }
                file.writeText(json.toString(4))
            } else {
                // Legacy update
                val (note, jsonArray) = readWorkoutFile(file)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = if (obj.has("id")) obj.getString("id") else "${obj.optString("tag", "")}_${obj.optString("uhrzeit", "")}"
                    if (id == set.id) {
                        obj.put("wiederholungen", newReps)
                        obj.put("gewicht", newWeight)
                        obj.put("isBodyweight", isBodyweight)
                        obj.put("isTimed", isTimed)
                        obj.put("1RM", calculate1RM(newWeight, newReps, isBodyweight, isTimed))
                        break
                    }
                }
                writeWorkoutFile(file, note, jsonArray)
            }
            loadHistory()
            refreshAnalyzeView()
        } catch (_: Exception) {}
    }

    private fun updateExerciseAdapters() {
        val exercises = getRecentExercises()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, exercises)
        exerciseDropdown.setAdapter(adapter)
        analyzeExerciseDropdown.setAdapter(adapter)

        // If we have master data, use it for the main dropdown
        if (exerciseMasterList.isNotEmpty()) {
            val masterNames = exerciseMasterList.map { it.name }
            val masterAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, masterNames)
            exerciseDropdown.setAdapter(masterAdapter)
            
            // Also update the Exercise Master Data RecyclerView
            exerciseAdapter.updateData(exerciseMasterList)
        }

        // UX: Pre-select the first exercise in Analyze if nothing is selected
        if (analyzeExerciseDropdown.text.isNullOrBlank() && exercises.isNotEmpty()) {
            analyzeExerciseDropdown.setText(exercises[0], false)
            refreshAnalyzeView()
        }
    }

    private fun getRecentExercises(): List<String> {
        val exercises = mutableListOf<String>()
        filesDir.listFiles { _, name -> name.startsWith("workout_") }?.sortedByDescending { it.name }?.forEach { file ->
            try {
                val (_, arr) = readWorkoutFile(file)
                for (i in 0 until arr.length()) {
                    val ex = arr.getJSONObject(i).getString("übung")
                    if (!exercises.contains(ex)) exercises.add(ex)
                }
            } catch (_: Exception) {}
        }
        return (exercises + listOf("Bankdrücken", "Kniebeugen", "Kreuzheben")).distinct()
    }

    private fun updateProgressionView(exerciseName: String) {
        val trimmedEx = exerciseName.trim()
        if (trimmedEx.isEmpty()) return
        
        val map = mutableMapOf<String, MutableList<Double>>()
        filesDir.listFiles { _, name -> name.startsWith("workout") }?.forEach { file ->
            try {
                val (sessionNote, jsonArray) = readWorkoutFile(file)
                val dateFromFilename = file.name.removePrefix("workout_").removePrefix("workout").removeSuffix(".json")

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val ex = obj.optString("übung", obj.optString("name", obj.optString("exercise", "")))
                    
                    if (ex.equals(trimmedEx, ignoreCase = true)) {
                        val date = obj.optString("date", obj.optString("tag", dateFromFilename))
                        val isBW = obj.optBoolean("isBodyweight", false)
                        val isT = obj.optBoolean("isTimed", obj.optString("trackingType") == TrackingType.DURATION.name)
                        
                        val weight = obj.optDouble("weightKg", obj.optDouble("gewicht", obj.optDouble("weight", 0.0)))
                        val reps = obj.optInt("reps", obj.optInt("wiederholungen", obj.optInt("durationSeconds", 0)))
                        
                        val current1RM = if (isT) {
                            if (weight > 0) weight * (1 + reps / 30.0) else reps.toDouble()
                        } else if (isBW) {
                            reps.toDouble()
                        } else {
                            weight * (1 + reps / 30.0)
                        }

                        val valStr = if (graphMode == ProgressionGraphView.GraphMode.ONE_RM) {
                            current1RM
                        } else {
                            if (isBW) reps.toDouble()
                            else if (isT) {
                                if (weight > 0) weight * reps else reps.toDouble()
                            } else {
                                reps * weight
                            }
                        }
                        map.getOrPut(date) { mutableListOf() }.add(valStr)
                    }
                }
            } catch (e: Exception) {
                Log.e("GymTracker", "Error processing file for progression: ${file.name}", e)
            }
        }
        val sessionMaxMap = map.mapValues { it.value.maxOrNull() ?: 0.0 }
        progressionGraph.setSessionData(map, graphMode, sessionMaxMap)
    }

    private fun loadLastExerciseData(exerciseName: String) {
        val ex = exerciseName.trim()
        if (ex.isBlank()) return
        
        lastRefIsBodyweight = false
        lastRefIsTimed = false
        lastRefWeight = 0.0
        lastRefOneRm = 0.0
        var found = false

        filesDir.listFiles { _, name -> name.startsWith("workout") }?.sortedByDescending { it.name }?.firstOrNull { file ->
            try {
                val jsonStr = file.readText()
                val json = JSONObject(jsonStr)
                if (json.has("exercises")) {
                    val exercisesArr = json.getJSONArray("exercises")
                    for (i in 0 until exercisesArr.length()) {
                        val obj = exercisesArr.getJSONObject(i)
                        if (obj.optString("name").equals(ex, ignoreCase = true)) {
                            val sets = obj.getJSONArray("sets")
                            if (sets.length() > 0) {
                                val lastSet = sets.getJSONObject(sets.length() - 1)
                                lastRefIsBodyweight = obj.optBoolean("isBodyweight", false)
                                lastRefIsTimed = obj.optString("trackingType") == TrackingType.DURATION.name
                                lastRefWeight = lastSet.optDouble("weightKg", 0.0)
                                val reps = if (lastRefIsTimed) lastSet.optInt("durationSeconds", 0) else lastSet.optInt("reps", 0)
                                
                                lastRefOneRm = calculate1RM(lastRefWeight, reps, lastRefIsBodyweight, lastRefIsTimed)
                                found = true
                                return@firstOrNull true
                            }
                        }
                    }
                } else {
                    // Legacy support
                    val (_, arr) = readWorkoutFile(file)
                    for (i in arr.length() - 1 downTo 0) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("übung", obj.optString("name", obj.optString("exercise", "")))
                        if (name.equals(ex, ignoreCase = true)) {
                            lastRefIsBodyweight = obj.optBoolean("isBodyweight", false)
                            lastRefIsTimed = obj.optBoolean("isTimed", obj.optString("trackingType") == TrackingType.DURATION.name)
                            lastRefWeight = obj.optDouble("weightKg", obj.optDouble("gewicht", obj.optDouble("weight", 0.0)))
                            
                            val reps = obj.optInt("reps", obj.optInt("wiederholungen", obj.optInt("durationSeconds", 0)))
                            
                            lastRefOneRm = calculate1RM(lastRefWeight, reps, lastRefIsBodyweight, lastRefIsTimed)
                            found = true
                            return@firstOrNull true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GymTracker", "Error loading last exercise data from ${file.name}", e)
            }
            false
        }

        if (found) {
            bodyweightCheckbox.isChecked = lastRefIsBodyweight
            timedCheckbox.isChecked = lastRefIsTimed
            
            // IMPORTANT: User requested weight to change ALWAYS when exercise changes
            if (!lastRefIsBodyweight) {
                weightInput.setText(lastRefWeight.toString())
            } else {
                weightInput.setText("0")
            }
            
            // Update 1RM display with the LAST value as reference
            if (lastRefIsTimed) {
                if (lastRefWeight > 0) {
                    oneRmResult.text = getString(R.string.last_1rm, lastRefOneRm)
                } else {
                    oneRmResult.text = getString(R.string.last_basis_wdh, lastRefOneRm) + " " + getString(R.string.time_hint)
                }
            } else {
                oneRmResult.text = if (lastRefIsBodyweight) 
                    getString(R.string.last_basis_wdh, lastRefOneRm)
                else 
                    getString(R.string.last_1rm, lastRefOneRm)
            }
            
            updateTrainingRanges(lastRefOneRm, lastRefIsBodyweight, lastRefIsTimed, lastRefWeight)
        } else {
            // Defaults if exercise is new or unknown
            oneRmResult.text = getString(R.string.one_rm_basis)
            trainingRanges.text = getString(R.string.ranges_basis)
        }
    }

    private fun updateSetNumberForExercise(exerciseName: String) {
        val ex = exerciseName.trim()
        if (ex.isEmpty()) {
            setNumberInput.setText("1")
            return
        }
        val file = File(filesDir, getTodayFileName())
        var max = 0
        if (file.exists()) {
            try {
                val jsonStr = file.readText()
                val json = JSONObject(jsonStr)
                if (json.has("exercises")) {
                    val exercisesArr = json.getJSONArray("exercises")
                    for (i in 0 until exercisesArr.length()) {
                        val obj = exercisesArr.getJSONObject(i)
                        val name = obj.optString("name", obj.optString("übung", "")).trim()
                        if (name.equals(ex, ignoreCase = true)) {
                            val sets = obj.optJSONArray("sets") ?: JSONArray()
                            for (j in 0 until sets.length()) {
                                val sObj = sets.getJSONObject(j)
                                val s = sObj.optInt("setNumber", sObj.optInt("satz", 0))
                                if (s > max) max = s
                            }
                        }
                    }
                } else {
                    // Legacy support
                    val (_, arr) = readWorkoutFile(file)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("übung", obj.optString("name", obj.optString("exercise", ""))).trim()
                        if (name.equals(ex, ignoreCase = true)) {
                            val s = obj.optInt("setNumber", obj.optInt("satz", 0))
                            if (s > max) max = s
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        setNumberInput.setText((max + 1).toString())
    }

    private fun updateTrainingRanges(oneRm: Double, isBodyweight: Boolean = false, isTimed: Boolean = false, weight: Double = 0.0) {
        val unit = if (isTimed && weight <= 0) getString(R.string.time_hint) else if (isBodyweight && !isTimed) "Reps" else "kg"
        val f = Locale.getDefault()
        
        // Increased field width to 28 for labels and 7.1 for weights to ensure perfect alignment
        val labelWidth = 26
        val line1 = String.format(f, "%-${labelWidth}s ~%7.1f %s", getString(R.string.range_endurance), oneRm * 0.45, unit)
        val line2 = String.format(f, "%-${labelWidth}s ~%7.1f %s", getString(R.string.range_strength_endurance), oneRm * 0.60, unit)
        val line3 = String.format(f, "%-${labelWidth}s ~%7.1f %s", getString(R.string.range_hypertrophy), oneRm * 0.75, unit)
        val line4 = String.format(f, "%-${labelWidth}s ~%7.1f %s", getString(R.string.range_maximal), oneRm * 0.95, unit)

        trainingRanges.text = "${getString(R.string.training_ranges_title)}\n$line1\n$line2\n$line3\n$line4"
    }


    private fun exportTodayOnly() {
        val file = File(filesDir, getTodayFileName())
        if (file.exists()) shareFile(file, "Today") else Toast.makeText(this, R.string.no_data_today, Toast.LENGTH_SHORT).show()
    }

    private fun exportAllData() {
        val backupContent = generateBackupJson()
        if (backupContent == null) {
            Toast.makeText(this, R.string.no_data_export, Toast.LENGTH_SHORT).show()
            return
        }
        val f = File(cacheDir, "gymtracker_backup.json")
        f.writeText(backupContent)
        shareFile(f, getString(R.string.backup_filename))
    }

    private fun generateBackupJson(): String? {
        val files = filesDir.listFiles { _, name -> name.startsWith("workout") && name.endsWith(".json") }
        if (files.isNullOrEmpty()) return null
        
        val backupObj = JSONObject()
        val sessionsArr = JSONArray()
        
        files.sortedBy { it.name }.forEach { f ->
            try {
                val content = f.readText()
                if (content.startsWith("{")) {
                    sessionsArr.put(JSONObject(content))
                } else if (content.startsWith("[")) {
                    sessionsArr.put(JSONArray(content))
                }
            } catch (e: Exception) {}
        }
        
        // Also include exercises master data
        val exercisesFile = File(filesDir, "exercises.json")
        if (exercisesFile.exists()) {
            try {
                backupObj.put("master_exercises", JSONArray(exercisesFile.readText()))
            } catch (e: Exception) {}
        }
        
        backupObj.put("sessions", sessionsArr)
        backupObj.put("backup_date", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()))
        backupObj.put("version", 2)
        
        return backupObj.toString(4)
    }

    private fun backupToGoogleDrive() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            putExtra(Intent.EXTRA_TITLE, "gymtracker_backup_$date.json")
        }
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
    }

    private fun exportToFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, EXPORT_TREE_REQUEST_CODE)
    }

    private fun restoreFromBackup() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                CREATE_FILE_REQUEST_CODE -> {
                    data.data?.let { uri ->
                        try {
                            val backupContent = generateBackupJson()
                            if (backupContent != null) {
                                contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    outputStream.write(backupContent.toByteArray())
                                }
                                Toast.makeText(this, R.string.saved_success, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Error saving to Drive", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                PICK_FILE_REQUEST_CODE -> {
                    data.data?.let { uri ->
                        MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.restore_confirm_title)
                            .setMessage(R.string.restore_confirm_message)
                            .setPositiveButton(R.string.yes) { _, _ -> performRestore(uri) }
                            .setNegativeButton(R.string.no, null)
                            .show()
                    }
                }
                EXPORT_TREE_REQUEST_CODE -> {
                    data.data?.let { treeUri ->
                        performExportToFolder(treeUri)
                    }
                }
            }
        }
    }

    private fun performExportToFolder(treeUri: Uri) {
        val resolver = contentResolver
        try {
            // Take persistable permission to allow future access if needed (though this is a one-time export)
            contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
            
            // Map of existing file names to their document URIs
            val existingFiles = mutableMapOf<String, Uri>()
            resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val id = cursor.getString(idIndex)
                    existingFiles[name] = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                }
            }
            
            val files = filesDir.listFiles() ?: return
            var count = 0
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

            files.forEach { file ->
                if (file.isFile) {
                    try {
                        val uri = existingFiles[file.name] ?: DocumentsContract.createDocument(resolver, parentUri, "application/json", file.name)
                        uri?.let {
                            resolver.openOutputStream(it, "wt")?.use { out ->
                                file.inputStream().use { it.copyTo(out) }
                            }
                            count++
                        }
                    } catch (e: Exception) {
                        Log.e("GymTracker", "Failed to export ${file.name}", e)
                    }
                }
            }
            Toast.makeText(this, getString(R.string.export_success, count), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_error, e.message), Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun performRestore(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (content.isNullOrBlank()) return

            val backupObj = JSONObject(content)
            val version = backupObj.optInt("version", 1)
            
            var sessionsImported = 0
            var exercisesImported = 0

            // Restore Exercises
            if (backupObj.has("master_exercises")) {
                val masterArr = backupObj.getJSONArray("master_exercises")
                val existingFile = File(filesDir, "exercises.json")
                val existingArr = if (existingFile.exists()) JSONArray(existingFile.readText()) else JSONArray()
                
                val existingNames = mutableSetOf<String>()
                for (i in 0 until existingArr.length()) {
                    existingNames.add(existingArr.getJSONObject(i).getString("name").lowercase())
                }

                for (i in 0 until masterArr.length()) {
                    val ex = masterArr.getJSONObject(i)
                    if (!existingNames.contains(ex.getString("name").lowercase())) {
                        existingArr.put(ex)
                        exercisesImported++
                    }
                }
                existingFile.writeText(existingArr.toString(4))
                loadExercises()
            }

            // Restore Sessions
            if (backupObj.has("sessions")) {
                val sessionsArr = backupObj.getJSONArray("sessions")
                for (i in 0 until sessionsArr.length()) {
                    val session = sessionsArr.get(i)
                    val sessionObj = when (session) {
                        is JSONObject -> session
                        is JSONArray -> {
                            // Legacy array root
                            if (session.length() > 0) {
                                val firstSet = session.getJSONObject(0)
                                val date = firstSet.optString("tag", extractDateFromFilename("unknown"))
                                migrateLegacyToHierarchical(JSONObject().apply { put("sets", session) }, date)
                            } else null
                        }
                        else -> null
                    }

                    if (sessionObj != null) {
                        val date = sessionObj.optString("date")
                        if (date.isNotEmpty()) {
                            val fileName = "workout_$date.json"
                            val file = File(filesDir, fileName)
                            
                            if (!file.exists()) {
                                file.writeText(sessionObj.toString(4))
                                sessionsImported++
                            } else {
                                // Merge logic: if file exists, we could merge exercises, but for now we skip to avoid mess
                                // Simple merge: add exercises that don't exist
                                val existingContent = file.readText()
                                try {
                                    val existingJson = JSONObject(existingContent)
                                    if (existingJson.has("exercises") && sessionObj.has("exercises")) {
                                        val existingExArr = existingJson.getJSONArray("exercises")
                                        val newExArr = sessionObj.getJSONArray("exercises")
                                        val existingExNames = mutableSetOf<String>()
                                        for (j in 0 until existingExArr.length()) {
                                            existingExNames.add(existingExArr.getJSONObject(j).getString("name").lowercase())
                                        }
                                        
                                        var mergedAny = false
                                        for (j in 0 until newExArr.length()) {
                                            val newEx = newExArr.getJSONObject(j)
                                            if (!existingExNames.contains(newEx.getString("name").lowercase())) {
                                                existingExArr.put(newEx)
                                                mergedAny = true
                                            }
                                        }
                                        if (mergedAny) {
                                            file.writeText(existingJson.toString(4))
                                            sessionsImported++
                                        }
                                    }
                                } catch (e: Exception) {
                                    // If existing is legacy and we have hierarchical, maybe overwrite?
                                    // For safety, if it's not hierarchical, we just skip or overwrite if it's very small
                                    if (!existingContent.contains("exercises")) {
                                        file.writeText(sessionObj.toString(4))
                                        sessionsImported++
                                    }
                                }
                            }
                        }
                    }
                }
            }

            loadHistory()
            updateExerciseAdapters()
            updateSessionStatus()
            
            Toast.makeText(this, getString(R.string.restore_success, sessionsImported, exercisesImported), Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.restore_error, e.message), Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    companion object {
        private const val CREATE_FILE_REQUEST_CODE = 1001
        private const val PICK_FILE_REQUEST_CODE = 1002
        private const val EXPORT_TREE_REQUEST_CODE = 1003
    }

    private fun shareFile(f: File, t: String) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, t))
    }

    private fun finishWorkout() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_finish_title).setMessage(R.string.dialog_finish_message).setPositiveButton(R.string.yes) { _, _ ->
            updateTodayNote(workoutNoteInputLarge.text.toString())
            exerciseDropdown.text.clear()
            repsInput.text?.clear()
            weightInput.text?.clear()
            setNumberInput.setText("1")
            oneRmResult.text = getString(R.string.one_rm_basis)
            trainingRanges.text = getString(R.string.ranges_basis)
            bodyweightCheckbox.isChecked = false
            timedCheckbox.isChecked = false
            workoutNoteInputLarge.text?.clear()
            updateSessionStatus()
        }.setNegativeButton(R.string.no, null).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("graph_mode", graphMode.name)
        outState.putInt("selected_item_id", bottomNavigation.selectedItemId)
    }

    private fun updateSessionStatus() {
        val files = filesDir.listFiles { _, name -> name.startsWith("workout_") }
        var total = 0
        files?.forEach { try { total += readWorkoutFile(it).second.length() } catch(e: Exception) {} }
        sessionStatus.text = getString(R.string.database_status, total, files?.size ?: 0)
    }
}
