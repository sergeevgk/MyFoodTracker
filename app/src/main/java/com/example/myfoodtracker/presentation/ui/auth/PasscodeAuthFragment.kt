package com.example.myfoodtracker.presentation.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.FragmentPasscodeAuthBinding
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.presentation.viewmodel.PasscodeAuthState
import com.example.myfoodtracker.presentation.viewmodel.PasscodeAuthViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class PasscodeAuthFragment : Fragment() {

    private var _binding: FragmentPasscodeAuthBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PasscodeAuthViewModel by viewModel()

    private var currentProfiles: List<UserProfile> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPasscodeAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProfiles()
    }

    private fun setupListeners() {
        binding.etPasscode.doAfterTextChanged {
            binding.tilPasscode.error = null
            viewModel.clearError()
        }

        binding.btnUnlock.setOnClickListener {
            val passcode = binding.etPasscode.text?.toString().orEmpty()
            viewModel.authenticate(passcode)
        }

        binding.btnAddNewProfile.setOnClickListener {
            findNavController().navigate(R.id.action_passcodeAuthFragment_to_profileSetupFragment)
        }

        binding.actvProfileSelector.setOnItemClickListener { _, _, position, _ ->
            if (position in currentProfiles.indices) {
                val selected = currentProfiles[position]
                viewModel.selectProfile(selected.id)
                binding.etPasscode.text?.clear()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is PasscodeAuthState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnUnlock.isEnabled = false
                }
                is PasscodeAuthState.NoProfiles -> {
                    binding.progressBar.visibility = View.GONE
                    if (findNavController().currentDestination?.id == R.id.passcodeAuthFragment) {
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.passcodeAuthFragment, true)
                            .build()
                        findNavController().navigate(
                            R.id.action_passcodeAuthFragment_to_profileSetupFragment,
                            null,
                            navOptions
                        )
                    }
                }
                is PasscodeAuthState.Content -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnUnlock.isEnabled = !state.isAuthenticating

                    if (binding.actvProfileSelector.adapter == null || currentProfiles != state.profiles) {
                        currentProfiles = state.profiles
                        val adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            state.profiles.map { it.username }
                        )
                        binding.actvProfileSelector.setAdapter(adapter)
                    }

                    state.selectedProfile?.let { selected ->
                        if (binding.actvProfileSelector.text?.toString() != selected.username) {
                            binding.actvProfileSelector.setText(selected.username, false)
                        }
                    }

                    binding.tilPasscode.error = state.passcodeError
                }
                is PasscodeAuthState.Authenticated -> {
                    binding.progressBar.visibility = View.GONE
                    binding.etPasscode.text?.clear()
                    if (findNavController().currentDestination?.id == R.id.passcodeAuthFragment) {
                        findNavController().navigate(R.id.action_passcodeAuthFragment_to_dashboardFragment)
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
