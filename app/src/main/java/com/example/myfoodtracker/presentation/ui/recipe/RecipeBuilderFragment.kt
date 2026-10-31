package com.example.myfoodtracker.presentation.ui.recipe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.FragmentRecipeBuilderBinding
import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.model.ServingUnit
import com.example.myfoodtracker.presentation.ui.recipe.RecipeListFragment.Companion.EXTRA_RECIPE_NAME
import com.example.myfoodtracker.presentation.ui.recipe.RecipeListFragment.Companion.REQUEST_RECIPE_SAVED
import com.example.myfoodtracker.presentation.ui.search.FoodSearchBottomSheet
import com.example.myfoodtracker.presentation.viewmodel.RecipeBuilderViewModel
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.LocalDate

class RecipeBuilderFragment : Fragment(),
    FoodSearchBottomSheet.Listener,
    RecipeIngredientQuantityDialogFragment.Listener {

    private var _binding: FragmentRecipeBuilderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecipeBuilderViewModel by viewModel()
    private lateinit var adapter: RecipeIngredientAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeBuilderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecipeIngredientAdapter { position ->
            viewModel.clearSaveError()
            binding.tvDraftsError.visibility = View.GONE
            viewModel.removeIngredient(position)
        }
        binding.rvRecipeIngredients.adapter = adapter

        // Restore the draft name BEFORE attaching the text watcher, so the
        // restoration does not clear the validation error or re-write into
        // the ViewModel.
        binding.etRecipeName.setText(viewModel.name.value.orEmpty())
        binding.etRecipeName.setSelection(binding.etRecipeName.text?.length ?: 0)
        binding.etRecipeName.doAfterTextChanged { text ->
            viewModel.setName(text?.toString().orEmpty())
            viewModel.clearSaveError()
            binding.tilRecipeName.error = null
        }

        viewModel.drafts.observe(viewLifecycleOwner) { drafts ->
            refreshDrafts(drafts)
        }

        viewModel.saveError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                if (error == getString(R.string.recipe_error_name) || binding.etRecipeName.text.isNullOrBlank()) {
                    if (binding.etRecipeName.text.isNullOrBlank()) {
                        binding.tilRecipeName.error = error
                    }
                } else {
                    binding.tvDraftsError.text = error
                    binding.tvDraftsError.visibility = View.VISIBLE
                }
            } else {
                binding.tilRecipeName.error = null
                binding.tvDraftsError.visibility = View.GONE
            }
        }

        binding.btnAddIngredient.setOnClickListener {
            if (!isAdded) return@setOnClickListener
            FoodSearchBottomSheet.newInstance(LocalDate.now().toString(), showQuickAdd = false)
                .show(childFragmentManager, "RECIPE_INGREDIENT_SEARCH")
        }

        binding.btnSaveRecipe.setOnClickListener {
            if (!isAdded) return@setOnClickListener
            val name = binding.etRecipeName.text?.toString().orEmpty()
            var valid = true
            if (name.isBlank()) {
                binding.tilRecipeName.error = getString(R.string.recipe_error_name)
                valid = false
            } else {
                binding.tilRecipeName.error = null
            }
            if (viewModel.drafts.value.orEmpty().isEmpty()) {
                binding.tvDraftsError.text = getString(R.string.recipe_error_empty)
                binding.tvDraftsError.visibility = View.VISIBLE
                valid = false
            } else {
                binding.tvDraftsError.visibility = View.GONE
            }
            if (!valid) return@setOnClickListener
            if (viewModel.saveRecipe(name)) {
                setFragmentResult(REQUEST_RECIPE_SAVED, bundleOf(EXTRA_RECIPE_NAME to name.trim()))
                findNavController().popBackStack()
            }
        }
    }

    override fun onFoodSelected(food: FoodItem) {
        if (!isAdded) return
        RecipeIngredientQuantityDialogFragment.newInstance(food)
            .show(childFragmentManager, "RECIPE_INGREDIENT_QUANTITY")
    }

    override fun onQuickAddRequested() {
        if (!isAdded) return
        Snackbar.make(
            binding.root,
            getString(R.string.recipe_quick_add_unavailable),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onIngredientConfirmed(food: FoodItem, quantity: Double, unit: ServingUnit) {
        if (!isAdded) return
        viewModel.clearSaveError()
        binding.tvDraftsError.visibility = View.GONE
        viewModel.addIngredient(NewRecipeIngredient.fromFoodItem(food, quantity, unit))
    }

    private fun refreshDrafts(drafts: List<NewRecipeIngredient>) {
        if (_binding == null) return
        adapter.submitList(drafts)
        if (drafts.isEmpty()) {
            binding.tvEmptyIngredients.visibility = View.VISIBLE
            binding.rvRecipeIngredients.visibility = View.GONE
        } else {
            binding.tvEmptyIngredients.visibility = View.GONE
            binding.rvRecipeIngredients.visibility = View.VISIBLE
            binding.tvDraftsError.visibility = View.GONE
        }
        val calories = drafts.sumOf { it.calories }
        val protein = drafts.sumOf { it.proteinG }
        val carbs = drafts.sumOf { it.carbsG }
        val fat = drafts.sumOf { it.fatG }
        binding.tvRecipeTotals.text = getString(
            R.string.recipe_totals_format,
            drafts.size,
            calories,
            protein,
            carbs,
            fat
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
