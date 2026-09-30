package com.example.myfoodtracker.presentation.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
                binding.cardDayPill.setCardBackgroundColor(Color.parseColor("#1B4D3E"))
                binding.cardDayPill.strokeColor = Color.parseColor("#1B4D3E")
                binding.cardDayPill.strokeWidth = 0
                binding.tvDayName.setTextColor(Color.WHITE)
                binding.tvDayNumber.setTextColor(Color.WHITE)
                binding.viewTodayDot.visibility = if (item.isToday) View.VISIBLE else View.GONE
                binding.viewTodayDot.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            } else {
                binding.cardDayPill.setCardBackgroundColor(Color.TRANSPARENT)
                binding.cardDayPill.strokeColor = Color.parseColor("#E2E8F0")
                binding.cardDayPill.strokeWidth = dpToPx(1)
                binding.tvDayName.setTextColor(Color.parseColor("#718096"))
                binding.tvDayNumber.setTextColor(Color.parseColor("#2D3748"))
                binding.viewTodayDot.visibility = if (item.isToday) View.VISIBLE else View.GONE
                binding.viewTodayDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B4D3E"))
            }

            val statusDesc = buildString {
                append("${item.dayName}, ${item.dayNumber}")
                if (item.isSelected) append(", selected")
                if (item.isToday) append(", today")
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
