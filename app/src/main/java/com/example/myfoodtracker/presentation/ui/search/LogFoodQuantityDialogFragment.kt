package com.example.myfoodtracker.presentation.ui.search

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.DialogLogFoodQuantityBinding
import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.model.ServingUnit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

class LogFoodQuantityDialogFragment : DialogFragment() {

    interface Listener {
        fun onLogFoodConfirmed(food: FoodItem, quantity: Double, unit: ServingUnit, slot: String)
    }

    private var _binding: DialogLogFoodQuantityBinding? = null
    private val binding get() = _binding!!

    private lateinit var food: FoodItem
    private val units = listOf("g", "ml", "servings")
    private val slots = listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        food = requireArguments().toFoodItem()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogLogFoodQuantityBinding.inflate(layoutInflater)

        binding.tvLogFoodTitle.text = food.name
        binding.tvLogFoodBrand.text = food.brand?.trim().orEmpty()
        binding.tvLogFoodBrand.visibility =
            if (food.brand?.trim().isNullOrEmpty()) android.view.View.GONE
            else android.view.View.VISIBLE

        binding.spinnerLogFoodUnit.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, units)
        )
        binding.spinnerLogFoodUnit.setText("g", false)
        binding.spinnerLogFoodSlot.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, slots)
        )
        binding.spinnerLogFoodSlot.setText("BREAKFAST", false)

        val previewWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updatePreview()
        }
        binding.etLogFoodQuantity.addTextChangedListener(previewWatcher)
        binding.spinnerLogFoodUnit.setOnItemClickListener { _, _, _, _ -> updatePreview() }
        binding.etLogFoodQuantity.requestFocus()
        updatePreview()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.log_food_quantity_title))
            .setView(binding.root)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_save), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val quantity = binding.etLogFoodQuantity.text?.toString()?.trim()
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
                var valid = true
                if (quantity == null || !quantity.isFinite() || quantity <= 0) {
                    binding.tilLogFoodQuantity.error = getString(R.string.log_food_error_quantity)
                    valid = false
                } else {
                    binding.tilLogFoodQuantity.error = null
                }
                val slot = binding.spinnerLogFoodSlot.text?.toString()?.trim()?.uppercase()
                    ?: "BREAKFAST"
                if (slot !in slots) {
                    binding.tilLogFoodSlot.error = getString(R.string.log_food_error_slot)
                    valid = false
                } else {
                    binding.tilLogFoodSlot.error = null
                }
                if (!valid) return@setOnClickListener
                val listener = parentFragment as? Listener ?: activity as? Listener
                listener?.onLogFoodConfirmed(
                    food,
                    quantity!!,
                    binding.spinnerLogFoodUnit.text?.toString()?.trim()?.lowercase()
                        .toServingUnit(),
                    slot
                )
                dialog.dismiss()
            }
        }
        return dialog
    }

    private fun updatePreview() {
        if (_binding == null) return
        val quantity = binding.etLogFoodQuantity.text?.toString()?.trim()
            ?.replace(',', '.')
            ?.toDoubleOrNull()
        val unit = binding.spinnerLogFoodUnit.text?.toString()?.trim()?.lowercase().toServingUnit()
        if (quantity == null || !quantity.isFinite() || quantity <= 0) {
            binding.tvLogFoodPreview.text = getString(
                R.string.log_food_preview_format, 0.0, 0.0, 0.0, 0.0
            )
            return
        }
        val grams = when (unit) {
            ServingUnit.G -> quantity
            ServingUnit.ML -> quantity
            ServingUnit.SERVINGS -> quantity * food.baseServingSize
        }
        if (!grams.isFinite() || grams <= 0) {
            binding.tvLogFoodPreview.text = getString(
                R.string.log_food_preview_format, 0.0, 0.0, 0.0, 0.0
            )
            return
        }
        val factor = grams / 100.0
        val calories = food.calories * factor
        val protein = food.proteinG * factor
        val carbs = food.carbsG * factor
        val fat = food.fatG * factor
        if (!calories.isFinite() || !protein.isFinite() || !carbs.isFinite() || !fat.isFinite()) {
            binding.tvLogFoodPreview.text = getString(
                R.string.log_food_preview_format, 0.0, 0.0, 0.0, 0.0
            )
            return
        }
        binding.tvLogFoodPreview.text = getString(
            R.string.log_food_preview_format, calories, protein, carbs, fat
        )
        binding.tvLogFoodPreview.contentDescription = getString(
            R.string.a11y_log_food_preview,
            String.format(Locale.getDefault(), "%.0f", calories),
            String.format(Locale.getDefault(), "%.0f", protein),
            String.format(Locale.getDefault(), "%.0f", carbs),
            String.format(Locale.getDefault(), "%.0f", fat)
        )
    }

    private fun String?.toServingUnit(): ServingUnit = when (this) {
        "ml" -> ServingUnit.ML
        "servings" -> ServingUnit.SERVINGS
        else -> ServingUnit.G
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_BRAND = "brand"
        private const val KEY_BARCODE = "barcode"
        private const val KEY_CUSTOM = "custom"
        private const val KEY_CAL = "cal"
        private const val KEY_PROTEIN = "protein"
        private const val KEY_CARBS = "carbs"
        private const val KEY_FAT = "fat"
        private const val KEY_FIBER = "fiber"
        private const val KEY_SUGAR = "sugar"
        private const val KEY_SODIUM = "sodium"
        private const val KEY_SERVING_SIZE = "serving_size"
        private const val KEY_SERVING_UNIT = "serving_unit"

        fun newInstance(food: FoodItem): LogFoodQuantityDialogFragment {
            return LogFoodQuantityDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(KEY_ID, food.id)
                    putString(KEY_NAME, food.name)
                    putString(KEY_BRAND, food.brand)
                    putString(KEY_BARCODE, food.barcode)
                    putBoolean(KEY_CUSTOM, food.isCustom)
                    putDouble(KEY_CAL, food.calories)
                    putDouble(KEY_PROTEIN, food.proteinG)
                    putDouble(KEY_CARBS, food.carbsG)
                    putDouble(KEY_FAT, food.fatG)
                    putDouble(KEY_FIBER, food.fiberG)
                    putDouble(KEY_SUGAR, food.sugarG)
                    putDouble(KEY_SODIUM, food.sodiumMg)
                    putDouble(KEY_SERVING_SIZE, food.baseServingSize)
                    putString(KEY_SERVING_UNIT, food.baseServingUnit)
                }
            }
        }

        private fun Bundle.toFoodItem(): FoodItem = FoodItem(
            id = getLong(KEY_ID),
            name = getString(KEY_NAME).orEmpty(),
            brand = getString(KEY_BRAND),
            barcode = getString(KEY_BARCODE),
            isCustom = getBoolean(KEY_CUSTOM),
            calories = getDouble(KEY_CAL),
            proteinG = getDouble(KEY_PROTEIN),
            carbsG = getDouble(KEY_CARBS),
            fatG = getDouble(KEY_FAT),
            fiberG = getDouble(KEY_FIBER),
            sugarG = getDouble(KEY_SUGAR),
            sodiumMg = getDouble(KEY_SODIUM),
            baseServingSize = getDouble(KEY_SERVING_SIZE, 100.0),
            baseServingUnit = getString(KEY_SERVING_UNIT) ?: "g"
        )
    }
}
