package com.example.myfoodtracker.presentation.ui.recipe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.FragmentRecipeListBinding
import com.example.myfoodtracker.presentation.viewmodel.RecipeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.viewModel

class RecipeListFragment : Fragment() {

    private var _binding: FragmentRecipeListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecipeViewModel by viewModel()
    private lateinit var adapter: RecipeAdapter

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

        adapter = RecipeAdapter { recipe ->
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

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_RECIPE_SAVED = "recipe_saved"
        const val EXTRA_RECIPE_NAME = "recipe_name"
    }
}
