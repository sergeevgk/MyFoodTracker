package com.example.myfoodtracker.presentation.ui.auth

import android.os.Bundle
import android.os.CountDownTimer
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

    // Per-second lockout countdown runs in the Fragment (Timer side of the View layer):
    // the VM only reports the initial wait via PasscodeAuthState.Content.lockoutRemainingSeconds.
    private var lockoutTimer: CountDownTimer? = null
    private var lockoutRemainingSeconds = 0

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
            // While the lockout countdown is active the wait message must survive typing
            if (lockoutTimer == null) {
                binding.tilPasscode.error = null
                viewModel.clearError()
            }
        }

        binding.btnUnlock.setOnClickListener {
            val passcode = binding.etPasscode.text?.toString().orEmpty()
            val rememberDevice = binding.cbRememberDevice.isChecked
            viewModel.authenticate(passcode, rememberDevice)
        }

        binding.btnAddNewProfile.setOnClickListener {
            findNavController().navigate(R.id.action_passcodeAuthFragment_to_profileSetupFragment)
        }

        binding.actvProfileSelector.setOnItemClickListener { _, _, position, _ ->
            if (position in currentProfiles.indices) {
                val selected = currentProfiles[position]
                lockoutTimer?.cancel()
                lockoutTimer = null
                lockoutRemainingSeconds = 0
                binding.tilPasscode.error = null
                binding.btnUnlock.isEnabled = true
                viewModel.selectProfile(selected.id)
                binding.etPasscode.text?.clear()
                binding.cbRememberDevice.isChecked = false
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

                    if (lockoutTimer != null) {
                        // Countdown already running: keep Unlock disabled and the
                        // remaining-wait visible regardless of intermediate state emissions
                        binding.btnUnlock.isEnabled = false
                        updateLockoutError()
                    } else {
                        binding.btnUnlock.isEnabled = !state.isAuthenticating && state.lockoutRemainingSeconds == 0
                        if (state.lockoutRemainingSeconds > 0) {
                            startLockoutCountdown(state.lockoutRemainingSeconds)
                        } else {
                            binding.tilPasscode.error = state.passcodeError
                        }
                    }
                }
                is PasscodeAuthState.Authenticated -> {
                    binding.progressBar.visibility = View.GONE
                    binding.etPasscode.text?.clear()
                    binding.cbRememberDevice.isChecked = false
                    if (findNavController().currentDestination?.id == R.id.passcodeAuthFragment) {
                        findNavController().navigate(R.id.action_passcodeAuthFragment_to_dashboardFragment)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lockoutTimer?.cancel()
        lockoutTimer = null
        _binding = null
    }

    private fun startLockoutCountdown(seconds: Int) {
        if (seconds <= 0) return
        lockoutTimer?.cancel()
        lockoutRemainingSeconds = seconds
        updateLockoutError()
        lockoutTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                lockoutRemainingSeconds = ((millisUntilFinished + 999) / 1000).toInt().coerceAtLeast(1)
                updateLockoutError()
            }

            override fun onFinish() {
                lockoutTimer = null
                lockoutRemainingSeconds = 0
                binding.tilPasscode.error = null
                binding.btnUnlock.isEnabled = true
                viewModel.clearLockout()
            }
        }.start()
    }

    private fun updateLockoutError() {
        binding.tilPasscode.error =
            getString(R.string.auth_error_lockout, lockoutRemainingSeconds)
    }
}
