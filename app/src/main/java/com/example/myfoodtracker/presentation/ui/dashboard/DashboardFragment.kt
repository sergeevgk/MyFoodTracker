package com.example.myfoodtracker.presentation.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.FragmentDashboardBinding
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.presentation.ui.dashboard.model.DashboardUiState
import com.example.myfoodtracker.presentation.viewmodel.DashboardViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.progressindicator.LinearProgressIndicator
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
        weekDayAdapter = WeekDayAdapter { selectedDate ->
            viewModel.selectDate(selectedDate)
        }
        binding.rvWeekDays.adapter = weekDayAdapter
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
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.tvWelcomeBanner.text = "Welcome, ${state.username}!"
            binding.tvActiveProfile.text = "Active Profile ID: ${state.profileId}"
            binding.tvActiveDateHeader.text = state.formattedDateHeader

            weekDayAdapter.submitList(state.weekDays)

            if (state.mealEntries.isEmpty()) {
                binding.tvEmptyMeals.visibility = View.VISIBLE
                binding.layoutMealEntries.visibility = View.GONE
            } else {
                binding.tvEmptyMeals.visibility = View.GONE
                binding.layoutMealEntries.visibility = View.VISIBLE
                binding.tvMealCount.text = "${state.mealEntries.size} meal(s) logged"
            }

            bindMacroHeader(state)
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
            binding.tvCaloriesValue.text = "$consumed / $target kcal"
            binding.tvCaloriesTarget.text = "$percent% of daily goal"
            binding.tvCaloriesTarget.visibility = View.VISIBLE
            binding.progressCalories.visibility = View.VISIBLE
            binding.progressCalories.setProgress(
                ((summary.totalCalories / calorieTarget).coerceIn(0.0, 1.0) * 100).toInt()
            )
            binding.cardMacroHeader.contentDescription =
                "Calories: $consumed of $target kilocalories consumed, $percent percent"
        } else {
            val consumed = String.format(locale, "%,.0f", summary.totalCalories)
            binding.tvCaloriesValue.text = "$consumed kcal"
            binding.tvCaloriesTarget.visibility = View.GONE
            binding.progressCalories.visibility = View.GONE
            binding.cardMacroHeader.contentDescription =
                "Calories: $consumed kilocalories consumed, no target set"
        }

        bindMacroRow(
            row = binding.layoutProteinRow,
            valueView = binding.tvProteinValue,
            targetView = binding.tvProteinTarget,
            progress = binding.progressProtein,
            total = summary.totalProteinG,
            target = goal?.targetProteinG,
            unit = "g",
            nutrient = "Protein"
        )
        bindMacroRow(
            row = binding.layoutCarbsRow,
            valueView = binding.tvCarbsValue,
            targetView = binding.tvCarbsTarget,
            progress = binding.progressCarbs,
            total = summary.totalCarbsG,
            target = goal?.targetCarbsG,
            unit = "g",
            nutrient = "Carbs"
        )
        bindMacroRow(
            row = binding.layoutFatRow,
            valueView = binding.tvFatValue,
            targetView = binding.tvFatTarget,
            progress = binding.progressFat,
            total = summary.totalFatG,
            target = goal?.targetFatG,
            unit = "g",
            nutrient = "Fat"
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
            valueView.text = "$consumed$unit"
            targetView.text = " / $targetStr$unit"
            progress.setProgress(((total / target).coerceIn(0.0, 1.0) * 100).toInt())
            row.contentDescription = "$nutrient: $consumed of $targetStr grams consumed, $percent percent"
        } else {
            row.visibility = View.GONE
        }
    }

    private fun showDatePicker(activeDate: LocalDate) {
        val currentSelectionMillis = activeDate
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Date")
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
