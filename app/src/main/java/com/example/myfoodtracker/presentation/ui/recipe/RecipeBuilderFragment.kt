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
import com.example.myfoodtracker.presentation.viewmodel.RecipeViewModel
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.LocalDate

class RecipeBuilderFragment : Fragment(),
    FoodSearchBottomSheet.Listener,
    RecipeIngredientQuantityDialogFragment.Listener {

    private var _binding: FragmentRecipeBuilderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecipeViewModel by viewModel()
    private lateinit var adapter: RecipeIngredientAdapter
    private val drafts = mutableListOf<NewRecipeIngredient>()

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
            if (position in drafts.indices) {
                drafts.removeAt(position)
                refreshDrafts()
            }
        }
        binding.rvRecipeIngredients.adapter = adapter

        binding.etRecipeName.doAfterTextChanged {
            binding.tilRecipeName.error = null
        }

        viewModel.saveError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                if (error == getString(R.string.recipe_error_name) || binding.etRecipeName.text.isNullOrBlank()) {
                    binding.tilRecipeName.error = error
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
            FoodSearchBottomSheet.newInstance(LocalDate.now().toString())
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
            if (drafts.isEmpty()) {
                binding.tvDraftsError.text = getString(R.string.recipe_error_empty)
                binding.tvDraftsError.visibility = View.VISIBLE
                valid = false
            } else {
                binding.tvDraftsError.visibility = View.GONE
            }
            if (!valid) return@setOnClickListener
            if (viewModel.saveRecipe(name, drafts.toList())) {
                setFragmentResult(REQUEST_RECIPE_SAVED, bundleOf(EXTRA_RECIPE_NAME to name.trim()))
                findNavController().popBackStack()
            }
        }

        refreshDrafts()
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
        drafts.add(NewRecipeIngredient.fromFoodItem(food, quantity, unit))
        refreshDrafts()
    }

    private fun refreshDrafts() {
        if (_binding == null) return
        adapter.submitList(drafts.toList())
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
