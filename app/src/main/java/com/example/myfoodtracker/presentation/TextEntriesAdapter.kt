package com.example.myfoodtracker.presentation

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myfoodtracker.R
import com.example.myfoodtracker.domain.model.FoodEntry

class TextEntriesAdapter(
    private val onTextChanged: (String, String) -> Unit,
    private val onDeleteClicked: (String) -> Unit
) : ListAdapter<FoodEntry, TextEntriesAdapter.ViewHolder>(FoodEntryDiffCallback()) {

    private var newlyAddedPosition: Int = -1

    fun setNewlyAddedPosition(position: Int) {
        newlyAddedPosition = position
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val editText: EditText = view.findViewById(R.id.edit_text_item)
        val deleteButton: ImageButton = view.findViewById(R.id.button_delete)
        var textWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_text_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return

        // Remove old watcher if any
        holder.textWatcher?.let { holder.editText.removeTextChangedListener(it) }

        // Bind text only if it has changed to prevent cursor jumping
        if (holder.editText.text.toString() != item.text) {
            holder.editText.setText(item.text)
        }

        // Set delete click
        holder.deleteButton.setOnClickListener {
            val latestPos = holder.adapterPosition
            if (latestPos != RecyclerView.NO_POSITION) {
                val currentItem = getItem(latestPos)
                if (currentItem != null) {
                    onDeleteClicked(currentItem.id)
                }
            }
        }

        // Add text watcher
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val latestPos = holder.adapterPosition
                if (latestPos != RecyclerView.NO_POSITION) {
                    val currentItem = getItem(latestPos)
                    if (currentItem != null) {
                        onTextChanged(currentItem.id, s?.toString() ?: "")
                    }
                }
            }
        }
        holder.editText.addTextChangedListener(watcher)
        holder.textWatcher = watcher

        // Autofocus newly added item
        if (position == newlyAddedPosition) {
            newlyAddedPosition = -1
            holder.editText.requestFocus()
            holder.editText.post {
                val imm = holder.editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(holder.editText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private class FoodEntryDiffCallback : DiffUtil.ItemCallback<FoodEntry>() {
        override fun areItemsTheSame(oldItem: FoodEntry, newItem: FoodEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FoodEntry, newItem: FoodEntry): Boolean {
            return oldItem.text == newItem.text
        }
    }
}
