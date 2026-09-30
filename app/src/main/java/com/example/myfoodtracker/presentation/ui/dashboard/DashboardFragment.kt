package com.example.myfoodtracker.presentation.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.FragmentDashboardBinding
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.presentation.viewmodel.DashboardViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
