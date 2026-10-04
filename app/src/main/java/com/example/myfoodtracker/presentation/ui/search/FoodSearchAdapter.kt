package com.example.myfoodtracker.presentation.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.ItemFoodSearchResultBinding
import com.example.myfoodtracker.domain.model.FoodItem

class FoodSearchAdapter(
    private val onItemClick: (FoodItem) -> Unit
) : ListAdapter<FoodItem, FoodSearchAdapter.FoodViewHolder>(FoodDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
        val binding = ItemFoodSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FoodViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FoodViewHolder(
        private val binding: ItemFoodSearchResultBinding,
        private val onItemClick: (FoodItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FoodItem) {
            val context = binding.root.context
            binding.tvFoodName.text = item.name
            val brand = item.brand?.trim().orEmpty()
            val macros = context.getString(
                R.string.meal_macros_format,
                item.calories,
                item.proteinG,
                item.carbsG,
                item.fatG
            )
            binding.tvFoodSubtitle.text = if (brand.isEmpty()) {
                macros
            } else {
                context.getString(R.string.food_search_row_subtitle_format, brand, macros)
            }
            binding.chipCustom.visibility = if (item.isCustom) View.VISIBLE else View.GONE
            val subtitleA11y = if (brand.isEmpty()) macros else "$brand, $macros"
            binding.root.contentDescription = context.getString(
                R.string.a11y_food_search_result,
                item.name,
                subtitleA11y,
                item.calories.toInt()
            )
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object FoodDiffCallback : DiffUtil.ItemCallback<FoodItem>() {
        override fun areItemsTheSame(oldItem: FoodItem, newItem: FoodItem): Boolean {
            return oldItem.id == newItem.id && oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: FoodItem, newItem: FoodItem): Boolean {
            return oldItem == newItem
        }
    }
}
