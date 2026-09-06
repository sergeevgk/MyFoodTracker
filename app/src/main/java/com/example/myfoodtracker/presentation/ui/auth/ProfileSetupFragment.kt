package com.example.myfoodtracker.presentation.ui.auth

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
        binding.etUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilUsername.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etPasscode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilPasscode.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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
