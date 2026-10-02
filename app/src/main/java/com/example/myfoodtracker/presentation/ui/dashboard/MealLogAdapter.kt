package com.example.myfoodtracker.presentation.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myfoodtracker.databinding.ItemMealLogBinding
import com.example.myfoodtracker.domain.model.MealEntry
import java.util.Locale

class MealLogAdapter : ListAdapter<MealEntry, MealLogAdapter.MealViewHolder>(MealDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val binding = ItemMealLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MealViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MealViewHolder(
        private val binding: ItemMealLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: MealEntry) {
            val foodName = entry.foods.firstOrNull()?.name ?: "Meal"
            binding.tvMealTitle.text = "${entry.title} · ${entry.time}"
            binding.tvMealFoodName.text = foodName

            var calories = 0.0
            var protein = 0.0
            var carbs = 0.0
            var fat = 0.0
            for (food in entry.foods) {
                calories += food.calories
                protein += food.protein
                carbs += food.carbs
                fat += food.fat
            }
            val locale = Locale.getDefault()
            binding.tvMealMacros.text = String.format(
                locale,
                "%.0f kcal · P %.0fg · C %.0fg · F %.0fg",
                calories,
                protein,
                carbs,
                fat
            )

            val kcalInt = calories.toInt()
            binding.root.contentDescription = "$foodName, ${entry.title}, $kcalInt kilocalories"
        }
    }

    companion object MealDiffCallback : DiffUtil.ItemCallback<MealEntry>() {
        override fun areItemsTheSame(oldItem: MealEntry, newItem: MealEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MealEntry, newItem: MealEntry): Boolean {
            return oldItem == newItem
        }
    }
}
