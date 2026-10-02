package com.example.myfoodtracker.presentation.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.DialogQuickAddLogBinding
import com.example.myfoodtracker.databinding.FragmentDashboardBinding
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.presentation.ui.dashboard.model.DashboardUiState
import com.example.myfoodtracker.presentation.viewmodel.DashboardViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val sessionRepository: SessionRepository by inject()
    private val viewModel: DashboardViewModel by viewModel()

    private lateinit var weekDayAdapter: WeekDayAdapter
    private lateinit var mealLogAdapter: MealLogAdapter

    private lateinit var swipeDeleteBackground: ColorDrawable
    private lateinit var swipeDeleteLabel: String
    private val swipeDeletePaint = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activeProfile = sessionRepository.getActiveProfile()
        if (activeProfile == null) {
            findNavController().navigate(R.id.action_dashboardFragment_to_passcodeAuthFragment)
            return
        }

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        swipeDeleteBackground = ColorDrawable(
            ContextCompat.getColor(requireContext(), R.color.danger_red)
        )
        swipeDeleteLabel = getString(R.string.action_delete)
        weekDayAdapter = WeekDayAdapter { selectedDate ->
            viewModel.selectDate(selectedDate)
        }
        binding.rvWeekDays.adapter = weekDayAdapter

        mealLogAdapter = MealLogAdapter()
        binding.rvMealEntries.adapter = mealLogAdapter
        binding.rvMealEntries.isNestedScrollingEnabled = false

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val entry = mealLogAdapter.currentList.getOrNull(position) ?: return
                viewModel.deleteMealEntry(entry.id)
                showUndoSnackbar(entry)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    val itemView = viewHolder.itemView
                    swipeDeleteBackground.setBounds(
                        itemView.right + dX.toInt(),
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                    swipeDeleteBackground.draw(c)
                    val metrics = itemView.resources.displayMetrics
                    swipeDeletePaint.textSize = 14f * metrics.scaledDensity
                    val textMargin = 16f * metrics.density
                    val centerY = (itemView.top + itemView.bottom) / 2f
                    val textY = centerY - (swipeDeletePaint.descent() + swipeDeletePaint.ascent()) / 2f
                    c.drawText(swipeDeleteLabel, itemView.right.toFloat() - textMargin, textY, swipeDeletePaint)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvMealEntries)
    }

    private fun setupListeners() {
        binding.btnPrevWeek.setOnClickListener {
            viewModel.previousWeek()
        }

        binding.btnNextWeek.setOnClickListener {
            viewModel.nextWeek()
        }

        binding.btnToday.setOnClickListener {
            viewModel.jumpToToday()
        }

        binding.ibCalendarPicker.setOnClickListener {
            val currentDate = viewModel.uiState.value?.activeDate ?: LocalDate.now()
            showDatePicker(currentDate)
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            findNavController().navigate(R.id.action_dashboardFragment_to_passcodeAuthFragment)
        }

        binding.btnQuickAdd.setOnClickListener {
            showQuickAddDialog()
        }

        binding.btnAddWater250.setOnClickListener {
            viewModel.logWaterPlus250()
            playWaterTapFeedback()
            val total = viewModel.uiState.value?.waterTotalMl ?: 0
            val target = viewModel.uiState.value?.dailyGoal?.targetWaterMl
            val totalStr = String.format(Locale.getDefault(), "%,d", total)
            val message = if (target != null && target > 0) {
                val targetStr = String.format(Locale.getDefault(), "%,d", target)
                getString(R.string.water_added_with_target, totalStr, targetStr)
            } else {
                getString(R.string.water_added_no_target, totalStr)
            }
            @Suppress("DEPRECATION")
            binding.cardWaterWidget.announceForAccessibility(message)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.tvWelcomeBanner.text = getString(R.string.welcome_banner_format, state.username)
            binding.tvActiveProfile.text = getString(R.string.active_profile_format, state.profileId)
            binding.tvActiveDateHeader.text = state.formattedDateHeader

            weekDayAdapter.submitList(state.weekDays)

            if (state.mealEntries.isEmpty()) {
                binding.tvEmptyMeals.visibility = View.VISIBLE
                binding.layoutMealEntries.visibility = View.GONE
            } else {
                binding.tvEmptyMeals.visibility = View.GONE
                binding.layoutMealEntries.visibility = View.VISIBLE
                binding.tvMealCount.text =
                    getString(R.string.meal_count_format, state.mealEntries.size)
            }
            mealLogAdapter.submitList(state.mealEntries)

            bindMacroHeader(state)
            bindWaterWidget(state)
        }
    }

    private fun bindMacroHeader(state: DashboardUiState) {
        val summary = state.dailySummary
        val goal = state.dailyGoal
        val locale = Locale.getDefault()

        val calorieTarget = goal?.targetCalories
        if (calorieTarget != null && calorieTarget > 0) {
            val consumed = String.format(locale, "%,.0f", summary.totalCalories)
            val target = String.format(locale, "%,.0f", calorieTarget)
            val percent = ((summary.totalCalories / calorieTarget) * 100).toInt()
            binding.tvCaloriesValue.text = getString(R.string.macro_value_with_target, consumed, target)
            binding.tvCaloriesTarget.text = getString(R.string.macro_percent_of_goal, percent)
            binding.tvCaloriesTarget.visibility = View.VISIBLE
            binding.progressCalories.visibility = View.VISIBLE
            binding.progressCalories.setProgress(
                ((summary.totalCalories / calorieTarget).coerceIn(0.0, 1.0) * 100).toInt()
            )
            binding.cardMacroHeader.contentDescription =
                getString(R.string.a11y_calories_with_target, consumed, target, percent)
        } else {
            val consumed = String.format(locale, "%,.0f", summary.totalCalories)
            binding.tvCaloriesValue.text = getString(R.string.macro_value_no_target, consumed)
            binding.tvCaloriesTarget.visibility = View.GONE
            binding.progressCalories.visibility = View.GONE
            binding.cardMacroHeader.contentDescription =
                getString(R.string.a11y_calories_no_target, consumed)
        }

        bindMacroRow(
            row = binding.layoutProteinRow,
            valueView = binding.tvProteinValue,
            targetView = binding.tvProteinTarget,
            progress = binding.progressProtein,
            total = summary.totalProteinG,
            target = goal?.targetProteinG,
            unit = "g",
            nutrient = getString(R.string.dashboard_macro_protein)
        )
        bindMacroRow(
            row = binding.layoutCarbsRow,
            valueView = binding.tvCarbsValue,
            targetView = binding.tvCarbsTarget,
            progress = binding.progressCarbs,
            total = summary.totalCarbsG,
            target = goal?.targetCarbsG,
            unit = "g",
            nutrient = getString(R.string.dashboard_macro_carbs)
        )
        bindMacroRow(
            row = binding.layoutFatRow,
            valueView = binding.tvFatValue,
            targetView = binding.tvFatTarget,
            progress = binding.progressFat,
            total = summary.totalFatG,
            target = goal?.targetFatG,
            unit = "g",
            nutrient = getString(R.string.dashboard_macro_fat)
        )
    }

    private fun bindMacroRow(
        row: View,
        valueView: TextView,
        targetView: TextView,
        progress: LinearProgressIndicator,
        total: Double,
        target: Double?,
        unit: String,
        nutrient: String
    ) {
        if (target != null && target > 0) {
            row.visibility = View.VISIBLE
            val locale = Locale.getDefault()
            val consumed = String.format(locale, "%.0f", total)
            val targetStr = String.format(locale, "%.0f", target)
            val percent = ((total / target) * 100).toInt()
            valueView.text = getString(R.string.macro_row_format, consumed, unit)
            targetView.text = getString(R.string.macro_row_target_format, targetStr, unit)
            progress.setProgress(((total / target).coerceIn(0.0, 1.0) * 100).toInt())
            row.contentDescription =
                getString(R.string.a11y_macro_row, nutrient, consumed, targetStr, percent)
        } else {
            row.visibility = View.GONE
        }
    }

    private fun bindWaterWidget(state: DashboardUiState) {
        val total = state.waterTotalMl
        val target = state.dailyGoal?.targetWaterMl
        val locale = Locale.getDefault()

        if (target != null && target > 0) {
            val consumed = String.format(locale, "%,d", total)
            val targetStr = String.format(locale, "%,d", target)
            val percent = ((total.toDouble() / target) * 100).toInt()
            binding.tvWaterValue.text = getString(R.string.water_value_with_target, consumed, targetStr)
            binding.tvWaterTarget.text = getString(R.string.macro_percent_of_goal, percent)
            binding.tvWaterTarget.visibility = View.VISIBLE
            binding.progressWater.visibility = View.VISIBLE
            binding.progressWater.setProgress(
                ((total.toDouble() / target).coerceIn(0.0, 1.0) * 100).toInt()
            )
            binding.cardWaterWidget.contentDescription =
                getString(R.string.a11y_water_with_target, consumed, targetStr, percent)
        } else {
            val consumed = String.format(locale, "%,d", total)
            binding.tvWaterValue.text = getString(R.string.water_value_no_target, consumed)
            binding.tvWaterTarget.visibility = View.GONE
            binding.progressWater.visibility = View.GONE
            binding.cardWaterWidget.contentDescription =
                getString(R.string.a11y_water_no_target, consumed)
        }
    }

    private fun playWaterTapFeedback() {
        if (!isAdded) return
        binding.progressWater.animate()
            .scaleX(1.06f)
            .scaleY(1.06f)
            .setDuration(90)
            .withEndAction {
                if (!isAdded) return@withEndAction
                binding.progressWater.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(120)
                    .start()
            }
            .start()
        binding.tvWaterValue.animate()
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(90)
            .withEndAction {
                if (!isAdded) return@withEndAction
                binding.tvWaterValue.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(120)
                    .start()
            }
            .start()
    }

    private fun showQuickAddDialog() {
        if (!isAdded) return
        val dialogBinding = DialogQuickAddLogBinding.inflate(layoutInflater)
        val slots = listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")
        dialogBinding.spinnerMealSlot.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, slots)
        )
        dialogBinding.spinnerMealSlot.setText("BREAKFAST", false)
        dialogBinding.etQuickAddName.requestFocus()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_quick_add_title))
            .setView(dialogBinding.root)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_save), null)
            .show()

        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.etQuickAddName.text?.toString().orEmpty()
            var valid = true
            if (name.isBlank()) {
                dialogBinding.tilQuickAddName.error = getString(R.string.quick_add_error_name)
                valid = false
            } else {
                dialogBinding.tilQuickAddName.error = null
            }

            val calories = parseQuickAddMacro(dialogBinding.etQuickAddCalories.text?.toString(), dialogBinding.tilQuickAddCalories)
            val protein = parseQuickAddMacro(dialogBinding.etQuickAddProtein.text?.toString(), dialogBinding.tilQuickAddProtein)
            val carbs = parseQuickAddMacro(dialogBinding.etQuickAddCarbs.text?.toString(), dialogBinding.tilQuickAddCarbs)
            val fat = parseQuickAddMacro(dialogBinding.etQuickAddFat.text?.toString(), dialogBinding.tilQuickAddFat)
            if (!valid || calories == null || protein == null || carbs == null || fat == null) return@setOnClickListener

            val slot = dialogBinding.spinnerMealSlot.text?.toString()?.trim()?.uppercase() ?: "BREAKFAST"
            if (slot !in slots) {
                dialogBinding.tilMealSlot.error = getString(R.string.quick_add_error_slot)
                return@setOnClickListener
            }
            dialogBinding.tilMealSlot.error = null

            viewModel.logQuickAdd(name.trim(), calories, protein, carbs, fat, slot)
            dialog.dismiss()
            if (!isAdded) return@setOnClickListener
            binding.root.announceForAccessibility(
                getString(
                    R.string.quick_add_logged_format,
                    name.trim(),
                    calories.toInt(),
                    slot
                )
            )
        }
    }

    private fun parseQuickAddMacro(
        raw: String?,
        layout: com.google.android.material.textfield.TextInputLayout
    ): Double? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            layout.error = null
            return 0.0
        }
        val value = trimmed.toDoubleOrNull()
        if (value == null || !value.isFinite() || value < 0) {
            layout.error = getString(R.string.quick_add_error_number)
            return null
        }
        layout.error = null
        return value
    }

    private fun showUndoSnackbar(deleted: MealEntry) {
        if (!isAdded) return
        val foodName = deleted.foods.firstOrNull()?.name
            ?: getString(R.string.meal_fallback_name)
        binding.root.announceForAccessibility(
            getString(R.string.a11y_deleted, foodName, deleted.title)
        )
        Snackbar.make(
            binding.root,
            getString(R.string.snackbar_deleted_format, foodName, deleted.title),
            5000
        )
            .setAction(getString(R.string.action_undo)) {
                if (!isAdded) return@setAction
                viewModel.restoreLastDeleted()
                if (!isAdded) return@setAction
                binding.root.announceForAccessibility(
                    getString(R.string.a11y_restored, foodName, deleted.title)
                )
            }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                        viewModel.clearPendingDelete()
                    }
                }
            })
            .show()
    }

    private fun showDatePicker(activeDate: LocalDate) {
        val currentSelectionMillis = activeDate
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.dialog_select_date_title))
            .setSelection(currentSelectionMillis)
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val selectedDate = Instant.ofEpochMilli(selection)
                .atZone(ZoneId.of("UTC"))
                .toLocalDate()
            viewModel.selectDate(selectedDate)
        }

        datePicker.show(parentFragmentManager, "MONTH_CALENDAR_PICKER")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
