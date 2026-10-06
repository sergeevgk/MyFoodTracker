package com.example.myfoodtracker.presentation.ui.search

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.DialogCreateCustomFoodBinding
import com.example.myfoodtracker.domain.model.FoodItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CreateCustomFoodDialogFragment : DialogFragment() {

    interface Listener {
        fun onCustomFoodConfirmed(
            name: String,
            brand: String?,
            baseServingSize: Double,
            baseServingUnit: String,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            fiberG: Double,
            sugarG: Double,
            sodiumMg: Double
        ): FoodItem?
    }

    private var _binding: DialogCreateCustomFoodBinding? = null
    private val binding get() = _binding!!

    private val units = listOf("g", "ml", "servings")

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCreateCustomFoodBinding.inflate(layoutInflater)

        binding.spinnerCustomFoodUnit.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, units)
        )
        if (savedInstanceState == null) {
            binding.spinnerCustomFoodUnit.setText("g", false)
            binding.etCustomFoodName.setText(arguments?.getString(ARG_PREFILL_NAME).orEmpty())
            binding.etCustomFoodServingSize.setText("100")
        }
        binding.etCustomFoodName.requestFocus()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.custom_food_title))
            .setView(binding.root)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_save), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.etCustomFoodName.text?.toString().orEmpty()
                val brand = binding.etCustomFoodBrand.text?.toString()
                val servingSize = binding.etCustomFoodServingSize.text?.toString()
                    ?.trim()?.replace(',', '.')?.toDoubleOrNull()
                val unit = binding.spinnerCustomFoodUnit.text?.toString()?.trim()?.lowercase()
                    .takeUnless { it.isNullOrEmpty() } ?: "g"
                val calories = parseRequiredMacro(
                    binding.etCustomFoodCalories.text?.toString(),
                    binding.tilCustomFoodCalories
                )
                val protein = parseRequiredMacro(
                    binding.etCustomFoodProtein.text?.toString(),
                    binding.tilCustomFoodProtein
                )
                val carbs = parseRequiredMacro(
                    binding.etCustomFoodCarbs.text?.toString(),
                    binding.tilCustomFoodCarbs
                )
                val fat = parseRequiredMacro(
                    binding.etCustomFoodFat.text?.toString(),
                    binding.tilCustomFoodFat
                )
                val fiber = parseOptionalMacro(
                    binding.etCustomFoodFiber.text?.toString(),
                    binding.tilCustomFoodFiber
                )
                val sugar = parseOptionalMacro(
                    binding.etCustomFoodSugar.text?.toString(),
                    binding.tilCustomFoodSugar
                )
                val sodium = parseOptionalMacro(
                    binding.etCustomFoodSodium.text?.toString(),
                    binding.tilCustomFoodSodium
                )

                var valid = true
                if (name.isBlank()) {
                    binding.tilCustomFoodName.error = getString(R.string.custom_food_error_name)
                    valid = false
                } else {
                    binding.tilCustomFoodName.error = null
                }
                if (servingSize == null || !servingSize.isFinite() || servingSize <= 0) {
                    binding.tilCustomFoodServingSize.error =
                        getString(R.string.custom_food_error_serving_size)
                    valid = false
                } else {
                    binding.tilCustomFoodServingSize.error = null
                }
                if (calories == null || protein == null || carbs == null || fat == null ||
                    fiber == null || sugar == null || sodium == null
                ) {
                    valid = false
                }
                if (!valid || servingSize == null) return@setOnClickListener

                // Fallback resolution covers dialog recreation lifecycles.
                val listener = parentFragment as? Listener ?: activity as? Listener
                val created = listener?.onCustomFoodConfirmed(
                    name, brand, servingSize, unit,
                    calories!!, protein!!, carbs!!, fat!!, fiber!!, sugar!!, sodium!!
                )
                if (created != null) {
                    dialog.dismiss()
                } else {
                    binding.tilCustomFoodName.error = getString(R.string.custom_food_error_save)
                }
            }
        }
        return dialog
    }

    private fun parseRequiredMacro(
        raw: String?,
        field: com.google.android.material.textfield.TextInputLayout
    ): Double? {
        val trimmed = raw?.trim()
        if (trimmed.isNullOrEmpty()) {
            field.error = getString(R.string.custom_food_error_required)
            return null
        }
        val value = trimmed.replace(',', '.').toDoubleOrNull()
        return if (value == null || !value.isFinite() || value < 0) {
            field.error = getString(R.string.custom_food_error_number)
            null
        } else {
            field.error = null
            value
        }
    }

    private fun parseOptionalMacro(
        raw: String?,
        field: com.google.android.material.textfield.TextInputLayout
    ): Double? {
        if (raw?.trim().isNullOrEmpty()) {
            field.error = null
            return 0.0
        }
        return parseRequiredMacro(raw, field)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PREFILL_NAME = "prefill_name"

        fun newInstance(prefillName: String): CreateCustomFoodDialogFragment {
            return CreateCustomFoodDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_PREFILL_NAME, prefillName.trim()) }
            }
        }
    }
}
