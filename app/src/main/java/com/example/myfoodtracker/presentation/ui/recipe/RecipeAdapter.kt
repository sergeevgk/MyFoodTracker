package com.example.myfoodtracker.presentation.ui.recipe

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.ItemRecipeBinding
import com.example.myfoodtracker.domain.model.Recipe

class RecipeAdapter(
    private val onDeleteClicked: (Recipe) -> Unit,
    private val onRecipeClicked: (Recipe) -> Unit
) : ListAdapter<Recipe, RecipeAdapter.RecipeViewHolder>(RecipeDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecipeViewHolder(binding, onDeleteClicked, onRecipeClicked)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RecipeViewHolder(
        private val binding: ItemRecipeBinding,
        private val onDeleteClicked: (Recipe) -> Unit,
        private val onRecipeClicked: (Recipe) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            val context = binding.root.context
            binding.tvRecipeName.text = recipe.name
            binding.tvRecipeSummary.text = context.getString(
                R.string.recipe_row_summary_format,
                recipe.ingredientCount,
                recipe.totalCalories,
                recipe.totalProteinG,
                recipe.totalCarbsG,
                recipe.totalFatG
            )
            binding.root.contentDescription = context.getString(
                R.string.a11y_recipe_detail_row,
                recipe.name,
                recipe.ingredientCount,
                recipe.totalCalories.toInt()
            )
            binding.ibDeleteRecipe.setOnClickListener { onDeleteClicked(recipe) }
            binding.root.setOnClickListener { onRecipeClicked(recipe) }
        }
    }

    companion object RecipeDiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
            return oldItem == newItem
        }
    }
}
