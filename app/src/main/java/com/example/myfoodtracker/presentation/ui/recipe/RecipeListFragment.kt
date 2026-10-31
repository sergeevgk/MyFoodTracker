package com.example.myfoodtracker.presentation.ui.recipe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.FragmentRecipeListBinding
import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.presentation.viewmodel.RecipeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.viewModel

class RecipeListFragment : Fragment(),
    LogRecipeDialogFragment.Listener,
    RecipeDetailDialogFragment.Listener {

    private var _binding: FragmentRecipeListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecipeViewModel by viewModel()
    private lateinit var adapter: RecipeAdapter

    /**
     * The dashboard's active date, handed over through the navigation argument.
     * Never fall back to today - a recipe must land on the date the user is viewing.
     */
    private val activeDate: String?
        get() = arguments?.getString(ARG_ACTIVE_DATE)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecipeAdapter(
            onDeleteClicked = { recipe -> confirmDelete(recipe) },
            onRecipeClicked = { recipe -> openRecipeDetailDialog(recipe) }
        )
        binding.rvRecipes.adapter = adapter

        binding.btnAddRecipe.setOnClickListener {
            findNavController().navigate(R.id.action_recipeListFragment_to_recipeBuilderFragment)
        }

        setFragmentResultListener(REQUEST_RECIPE_SAVED) { _, bundle ->
            val name = bundle.getString(EXTRA_RECIPE_NAME).orEmpty()
            if (!isAdded || name.isBlank()) return@setFragmentResultListener
            val message = getString(R.string.recipe_saved_format, name)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            binding.root.announceForAccessibility(message)
        }

        viewModel.recipes.observe(viewLifecycleOwner) { recipes ->
            adapter.submitList(recipes)
            if (recipes.isEmpty()) {
                binding.tvEmptyRecipes.visibility = View.VISIBLE
                binding.rvRecipes.visibility = View.GONE
            } else {
                binding.tvEmptyRecipes.visibility = View.GONE
                binding.rvRecipes.visibility = View.VISIBLE
            }
        }
    }

    private fun confirmDelete(recipe: Recipe) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_delete_recipe_title))
            .setMessage(getString(R.string.dialog_delete_recipe_message, recipe.name))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                viewModel.deleteRecipe(recipe.id)
                if (!isAdded) return@setPositiveButton
                val message = getString(R.string.recipe_deleted_format, recipe.name)
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                binding.root.announceForAccessibility(message)
            }
            .show()
    }

    private fun openRecipeDetailDialog(recipe: Recipe) {
        if (!isAdded) return
        val ingredientTemplate = getString(R.string.recipe_detail_ingredient_line)
        val ingredientLines = recipe.ingredients.joinToString("\n") { ingredient ->
            RecipeDetailFormatter.formatIngredientLine(
                ingredient = ingredient,
                template = ingredientTemplate
            )
        }
        val totals = RecipeDetailFormatter.formatTotalsSummary(
            recipe = recipe,
            template = getString(R.string.recipe_totals_format)
        )
        val canLog = !activeDate.isNullOrBlank()

        RecipeDetailDialogFragment.newInstance(
            recipeId = recipe.id,
            name = recipe.name,
            ingredientsText = ingredientLines,
            totalsText = totals,
            canLog = canLog
        ).show(childFragmentManager, TAG_RECIPE_DETAIL)
    }

    override fun onLogRecipeRequested(recipeId: String) {
        if (!isAdded) return
        val recipe = viewModel.recipes.value?.firstOrNull { it.id == recipeId }
        if (recipe == null) {
            Snackbar.make(
                binding.root,
                getString(R.string.log_recipe_error_missing),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        openLogDialog(recipe)
    }

    private fun openLogDialog(recipe: Recipe) {
        if (!isAdded) return
        val date = activeDate
        if (date.isNullOrBlank()) {
            Snackbar.make(
                binding.root,
                getString(R.string.log_recipe_error_date),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        val ingredientLines = recipe.ingredients.joinToString("\n") { ingredient ->
            getString(
                R.string.log_recipe_ingredient_line,
                ingredient.foodName,
                RecipeDetailFormatter.formatQuantity(ingredient.quantityGrams),
                ingredient.unit,
                ingredient.calories
            )
        }
        val totals = getString(
            R.string.recipe_totals_format,
            recipe.ingredientCount,
            recipe.totalCalories,
            recipe.totalProteinG,
            recipe.totalCarbsG,
            recipe.totalFatG
        )
        LogRecipeDialogFragment.newInstance(
            recipeId = recipe.id,
            name = recipe.name,
            ingredientsText = ingredientLines,
            totalsText = totals
        ).show(childFragmentManager, TAG_LOG_RECIPE)
    }

    override fun onLogRecipeConfirmed(recipeId: String, mealSlot: String): String? {
        val date = activeDate
        if (date.isNullOrBlank()) return getString(R.string.log_recipe_error_date)

        val recipe = viewModel.recipes.value?.firstOrNull { it.id == recipeId }
        val recipeName = recipe?.name.orEmpty()
        val success = viewModel.logRecipe(recipeId, mealSlot, date)
        if (!success) {
            return mapLogError(viewModel.logError.value)
        }

        // Result must be set before popping so the dashboard can reload date `date`.
        setFragmentResult(
            REQUEST_RECIPE_LOGGED,
            bundleOf(
                EXTRA_LOGGED_RECIPE_NAME to recipeName,
                EXTRA_LOGGED_SLOT to mealSlot,
                EXTRA_LOGGED_DATE to date,
                EXTRA_LOGGED_INGREDIENT_COUNT to (recipe?.ingredientCount ?: 0)
            )
        )
        findNavController().popBackStack()
        return null
    }

    /** Domain throws plain literals; map them to localized copy for the dialog. */
    private fun mapLogError(message: String?): String = when (message) {
        "Missing log date" -> getString(R.string.log_recipe_error_date)
        "Invalid meal slot" -> getString(R.string.log_food_error_slot)
        "Recipe not found" -> getString(R.string.log_recipe_error_missing)
        "Add at least one ingredient" -> getString(R.string.recipe_error_empty)
        "Enter a name" -> getString(R.string.quick_add_error_name)
        "Enter a quantity greater than 0" -> getString(R.string.log_food_error_quantity)
        "Enter 0 or more" -> getString(R.string.quick_add_error_number)
        else -> message ?: getString(R.string.log_recipe_error_missing)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ACTIVE_DATE = "activeDate"

        const val REQUEST_RECIPE_SAVED = "recipe_saved"
        const val EXTRA_RECIPE_NAME = "recipe_name"

        const val REQUEST_RECIPE_LOGGED = "recipe_logged"
        const val EXTRA_LOGGED_RECIPE_NAME = "logged_recipe_name"
        const val EXTRA_LOGGED_SLOT = "logged_slot"
        const val EXTRA_LOGGED_DATE = "logged_date"
        const val EXTRA_LOGGED_INGREDIENT_COUNT = "logged_ingredient_count"

        private const val TAG_LOG_RECIPE = "LOG_RECIPE"
        private const val TAG_RECIPE_DETAIL = "RECIPE_DETAIL"
    }
}
