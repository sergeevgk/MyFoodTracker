package com.example.myfoodtracker.presentation.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.ItemWeekDayBinding
import com.example.myfoodtracker.presentation.ui.dashboard.model.DayItem
import java.time.LocalDate

class WeekDayAdapter(
    private val onDayClicked: (LocalDate) -> Unit
) : ListAdapter<DayItem, WeekDayAdapter.DayViewHolder>(DayDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = ItemWeekDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DayViewHolder(binding, onDayClicked)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DayViewHolder(
        private val binding: ItemWeekDayBinding,
        private val onDayClicked: (LocalDate) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DayItem) {
            binding.tvDayName.text = item.dayName
            binding.tvDayNumber.text = item.dayNumber

            val context = binding.root.context
            if (item.isSelected) {
                binding.cardDayPill.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.primary_forest)
                )
                binding.cardDayPill.strokeColor =
                    ContextCompat.getColor(context, R.color.primary_forest)
                binding.cardDayPill.strokeWidth = 0
                binding.tvDayName.setTextColor(Color.WHITE)
                binding.tvDayNumber.setTextColor(Color.WHITE)
                binding.viewTodayDot.visibility = if (item.isToday) View.VISIBLE else View.GONE
                binding.viewTodayDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            } else {
                binding.cardDayPill.setCardBackgroundColor(Color.TRANSPARENT)
                binding.cardDayPill.strokeColor =
                    ContextCompat.getColor(context, R.color.surface_stroke)
                binding.cardDayPill.strokeWidth = dpToPx(1)
                binding.tvDayName.setTextColor(
                    ContextCompat.getColor(context, R.color.text_secondary)
                )
                binding.tvDayNumber.setTextColor(
                    ContextCompat.getColor(context, R.color.text_primary)
                )
                binding.viewTodayDot.visibility = if (item.isToday) View.VISIBLE else View.GONE
                binding.viewTodayDot.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.primary_forest)
                )
            }

            val statusDesc = buildString {
                append(context.getString(R.string.a11y_day_base, item.dayName, item.dayNumber))
                if (item.isSelected) append(context.getString(R.string.a11y_day_selected_suffix))
                if (item.isToday) append(context.getString(R.string.a11y_day_today_suffix))
            }
            binding.root.contentDescription = statusDesc

            binding.root.setOnClickListener {
                onDayClicked(item.date)
            }
        }

        private fun dpToPx(dp: Int): Int {
            return (dp * binding.root.resources.displayMetrics.density).toInt()
        }
    }

    companion object DayDiffCallback : DiffUtil.ItemCallback<DayItem>() {
        override fun areItemsTheSame(oldItem: DayItem, newItem: DayItem): Boolean {
            return oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: DayItem, newItem: DayItem): Boolean {
            return oldItem == newItem
        }
    }
}
