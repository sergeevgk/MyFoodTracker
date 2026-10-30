package com.example.myfoodtracker.presentation.ui.recipe

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.DialogLogRecipeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Confirmation dialog for logging a saved recipe. It collects the meal slot only -
 * grams and macros come from the recipe snapshot and are never re-scaled here.
 *
 * The parent fragment performs the write through its own ViewModel (see
 * [RecipeListFragment]) and returns a display string on failure.
 */
class LogRecipeDialogFragment : DialogFragment() {

    interface Listener {
        /** Returns null on success (dialog dismisses) or an error message to display. */
        fun onLogRecipeConfirmed(recipeId: String, mealSlot: String): String?
    }

    private var _binding: DialogLogRecipeBinding? = null
    private val binding get() = _binding!!

    private val slots = listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")

    private var recipeId: String = ""
    private var recipeName: String = ""
    private var ingredientsText: String = ""
    private var totalsText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        recipeId = args.getString(ARG_RECIPE_ID).orEmpty()
        recipeName = args.getString(ARG_NAME).orEmpty()
        ingredientsText = args.getString(ARG_INGREDIENTS).orEmpty()
        totalsText = args.getString(ARG_TOTALS).orEmpty()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogLogRecipeBinding.inflate(layoutInflater)

        binding.tvLogRecipeName.text = recipeName
        binding.tvLogRecipeIngredients.text = ingredientsText
        binding.tvLogRecipeTotals.text = totalsText

        capIngredientScrollHeight()

        binding.spinnerLogRecipeSlot.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, slots)
        )
        val initialSlot = savedInstanceState?.getString(KEY_SELECTED_SLOT) ?: "BREAKFAST"
        binding.spinnerLogRecipeSlot.setText(initialSlot, false)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.log_recipe_title))
            .setView(binding.root)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_log), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val slot = binding.spinnerLogRecipeSlot.text?.toString()?.trim()?.uppercase()
                    ?: "BREAKFAST"
                if (slot !in slots) {
                    binding.tilLogRecipeSlot.error = getString(R.string.log_food_error_slot)
                    return@setOnClickListener
                }
                binding.tilLogRecipeSlot.error = null

                val listener = parentFragment as? Listener ?: activity as? Listener
                val error = listener?.onLogRecipeConfirmed(recipeId, slot)
                if (error.isNullOrBlank()) {
                    dialog.dismiss()
                } else {
                    showError(error)
                }
            }
        }
        return dialog
    }

    /**
     * A long recipe must not push the slot picker off screen. `android:maxHeight` is
     * not honoured by ScrollView, so the cap is applied after the first measurement.
     */
    private fun capIngredientScrollHeight() {
        val scroll = _binding?.scrollLogRecipeIngredients ?: return
        scroll.post {
            // The runnable can execute after the dialog fragment was detached
            // (4.2-D1): `Fragment.resources` is unavailable then, and the
            // captured `scroll` view reference is stale.
            if (!isAdded) return@post
            val maxHeight = resources.getDimensionPixelSize(
                R.dimen.dialog_log_recipe_ingredients_max_height
            )
            if (scroll.height > maxHeight) {
                val params = scroll.layoutParams
                params.height = maxHeight
                scroll.layoutParams = params
            }
        }
    }

    private fun showError(message: String) {
        val binding = _binding ?: return
        binding.tvLogRecipeError.text = message
        binding.tvLogRecipeError.visibility = View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.spinnerLogRecipeSlot?.text?.toString()?.let {
            outState.putString(KEY_SELECTED_SLOT, it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_SELECTED_SLOT = "selected_slot"
        private const val ARG_RECIPE_ID = "recipeId"
        private const val ARG_NAME = "name"
        private const val ARG_INGREDIENTS = "ingredients"
        private const val ARG_TOTALS = "totals"

        fun newInstance(
            recipeId: String,
            name: String,
            ingredientsText: String,
            totalsText: String
        ): LogRecipeDialogFragment = LogRecipeDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_RECIPE_ID, recipeId)
                putString(ARG_NAME, name)
                putString(ARG_INGREDIENTS, ingredientsText)
                putString(ARG_TOTALS, totalsText)
            }
        }
    }
}