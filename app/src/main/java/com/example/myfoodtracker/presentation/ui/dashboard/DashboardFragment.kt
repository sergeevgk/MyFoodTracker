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
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
import org.koin.android.ext.android.inject

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val sessionRepository: SessionRepository by inject()
    private val logoutUseCase: LogoutUseCase by inject()

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

        binding.tvWelcomeBanner.text = "Welcome, ${activeProfile.username}!"
        binding.tvActiveProfile.text = "Active Profile ID: ${activeProfile.id}"

        binding.btnLogout.setOnClickListener {
            logoutUseCase()
            findNavController().navigate(R.id.action_dashboardFragment_to_passcodeAuthFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
