package com.example.myfoodtracker.presentation.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.example.myfoodtracker.databinding.FragmentProfileSetupBinding
import com.example.myfoodtracker.presentation.viewmodel.ProfileSetupState
import com.example.myfoodtracker.presentation.viewmodel.ProfileSetupViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class ProfileSetupFragment : Fragment() {

    private var _binding: FragmentProfileSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileSetupViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.etUsername.doAfterTextChanged { binding.tilUsername.error = null }
        binding.etPasscode.doAfterTextChanged { binding.tilPasscode.error = null }
        binding.etCalorieTarget.doAfterTextChanged { binding.tilCalorieTarget.error = null }
        binding.etProteinTarget.doAfterTextChanged { binding.tilProteinTarget.error = null }
        binding.etCarbTarget.doAfterTextChanged { binding.tilCarbTarget.error = null }
        binding.etFatTarget.doAfterTextChanged { binding.tilFatTarget.error = null }
        binding.etWaterTarget.doAfterTextChanged { binding.tilWaterTarget.error = null }

        binding.btnCreateProfile.setOnClickListener {
            val username = binding.etUsername.text?.toString().orEmpty()
            val passcode = binding.etPasscode.text?.toString().orEmpty()
            val calorieTarget = binding.etCalorieTarget.text?.toString()
            val proteinTarget = binding.etProteinTarget.text?.toString()
            val carbTarget = binding.etCarbTarget.text?.toString()
            val fatTarget = binding.etFatTarget.text?.toString()
            val waterTarget = binding.etWaterTarget.text?.toString()

            viewModel.createProfile(
                username = username,
                passcode = passcode,
                calorieTargetStr = calorieTarget,
                proteinTargetStr = proteinTarget,
                carbTargetStr = carbTarget,
                fatTargetStr = fatTarget,
                waterTargetStr = waterTarget
            )
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileSetupState.Idle -> {
                    binding.btnCreateProfile.isEnabled = true
                }
                is ProfileSetupState.Loading -> {
                    binding.btnCreateProfile.isEnabled = false
                }
                is ProfileSetupState.Success -> {
                    binding.btnCreateProfile.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        "Profile '${state.userProfile.username}' created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is ProfileSetupState.Error -> {
                    binding.btnCreateProfile.isEnabled = true
                    binding.tilUsername.error = state.usernameError
                    binding.tilPasscode.error = state.passcodeError
                    binding.tilCalorieTarget.error = state.calorieError
                    binding.tilProteinTarget.error = state.proteinError
                    binding.tilCarbTarget.error = state.carbError
                    binding.tilFatTarget.error = state.fatError
                    binding.tilWaterTarget.error = state.waterError

                    if (state.generalError != null) {
                        Toast.makeText(requireContext(), state.generalError, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
