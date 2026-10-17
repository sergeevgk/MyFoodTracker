package com.example.myfoodtracker.presentation.ui.recipe

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.ItemRecipeIngredientBinding
import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import java.util.Locale

class RecipeIngredientAdapter(
    private val onRemoveClicked: (Int) -> Unit
) : ListAdapter<NewRecipeIngredient, RecipeIngredientAdapter.IngredientViewHolder>(IngredientDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val binding = ItemRecipeIngredientBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return IngredientViewHolder(binding, onRemoveClicked)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class IngredientViewHolder(
        private val binding: ItemRecipeIngredientBinding,
        private val onRemoveClicked: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(ingredient: NewRecipeIngredient) {
            val context = binding.root.context
            binding.tvIngredientName.text = ingredient.foodName
            val quantityLabel = if (ingredient.quantityGrams % 1.0 == 0.0) {
                String.format(Locale.getDefault(), "%,.0f", ingredient.quantityGrams)
            } else {
                String.format(Locale.getDefault(), "%,.1f", ingredient.quantityGrams)
            }
            binding.tvIngredientQuantity.text = context.getString(
                R.string.recipe_ingredient_quantity_format,
                quantityLabel,
                ingredient.unit
            )
            binding.tvIngredientMacros.text = context.getString(
                R.string.meal_macros_format,
                ingredient.calories,
                ingredient.proteinG,
                ingredient.carbsG,
                ingredient.fatG
            )
            binding.ibRemoveIngredient.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onRemoveClicked(pos)
                }
            }
        }
    }

    companion object IngredientDiffCallback : DiffUtil.ItemCallback<NewRecipeIngredient>() {
        override fun areItemsTheSame(
            oldItem: NewRecipeIngredient,
            newItem: NewRecipeIngredient
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: NewRecipeIngredient,
            newItem: NewRecipeIngredient
        ): Boolean {
            return oldItem == newItem
        }
    }
}
