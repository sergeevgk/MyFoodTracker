package com.example.myfoodtracker.presentation.ui.recipe

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.DialogRecipeDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Read-only detail inspection dialog for a saved recipe.
 * Displays the complete ingredient breakdown with quantities and individual macros,
 * as well as the summed totals.
 *
 * If [canLog] is true (i.e. active date is available), provides a "Log" action button
 * to proceed to the meal slot confirmation dialog.
 */
class RecipeDetailDialogFragment : DialogFragment() {

    interface Listener {
        fun onLogRecipeRequested(recipeId: String)
    }

    private var _binding: DialogRecipeDetailBinding? = null
    private val binding get() = _binding!!

    private var recipeId: String = ""
    private var recipeName: String = ""
    private var ingredientsText: String = ""
    private var totalsText: String = ""
    private var canLog: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        recipeId = args.getString(ARG_RECIPE_ID).orEmpty()
        recipeName = args.getString(ARG_NAME).orEmpty()
        ingredientsText = args.getString(ARG_INGREDIENTS).orEmpty()
        totalsText = args.getString(ARG_TOTALS).orEmpty()
        canLog = args.getBoolean(ARG_CAN_LOG, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogRecipeDetailBinding.inflate(layoutInflater)

        binding.tvDetailRecipeName.text = recipeName
        binding.tvDetailRecipeIngredients.text = ingredientsText
        binding.tvDetailRecipeTotals.text = totalsText

        capIngredientScrollHeight()

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.recipe_detail_title))
            .setView(binding.root)
            .setNegativeButton(getString(R.string.action_close), null)

        if (canLog) {
            builder.setPositiveButton(getString(R.string.action_log)) { _, _ ->
                val listener = parentFragment as? Listener ?: activity as? Listener
                listener?.onLogRecipeRequested(recipeId)
            }
        }

        return builder.create()
    }

    private fun capIngredientScrollHeight() {
        val scroll = _binding?.scrollDetailRecipeIngredients ?: return
        scroll.post {
            if (!isAdded || _binding == null) return@post
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_RECIPE_ID = "recipe_id"
        private const val ARG_NAME = "recipe_name"
        private const val ARG_INGREDIENTS = "recipe_ingredients"
        private const val ARG_TOTALS = "recipe_totals"
        private const val ARG_CAN_LOG = "can_log"

        fun newInstance(
            recipeId: String,
            name: String,
            ingredientsText: String,
            totalsText: String,
            canLog: Boolean
        ): RecipeDetailDialogFragment {
            return RecipeDetailDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RECIPE_ID, recipeId)
                    putString(ARG_NAME, name)
                    putString(ARG_INGREDIENTS, ingredientsText)
                    putString(ARG_TOTALS, totalsText)
                    putBoolean(ARG_CAN_LOG, canLog)
                }
            }
        }
    }
}
