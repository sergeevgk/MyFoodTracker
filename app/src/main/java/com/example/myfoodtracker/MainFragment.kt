package com.example.myfoodtracker

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myfoodtracker.databinding.FragmentFirstBinding
import org.json.JSONArray

class MainFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: TextEntriesAdapter
    private val itemsList = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load items from persistence
        itemsList.addAll(loadItems())

        // Initialize adapter
        adapter = TextEntriesAdapter(
            itemsList,
            onTextChanged = { position, newText ->
                itemsList[position] = newText
                saveItems(itemsList)
            },
            onDeleteClicked = { position ->
                itemsList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, itemsList.size - position)
                saveItems(itemsList)
            }
        )

        binding.recyclerViewEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewEntries.adapter = adapter

        // Setup add button
        binding.buttonAdd.setOnClickListener {
            val newPosition = itemsList.size
            itemsList.add("")
            adapter.setNewlyAddedPosition(newPosition)
            adapter.notifyItemInserted(newPosition)
            binding.recyclerViewEntries.scrollToPosition(newPosition)
            saveItems(itemsList)
        }
    }

    private fun saveItems(items: List<String>) {
        val sharedPreferences = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray(items)
        sharedPreferences.edit().putString("text_items", jsonArray.toString()).apply()
    }

    private fun loadItems(): List<String> {
        val sharedPreferences = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val jsonString = sharedPreferences.getString("text_items", null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getString(i))
                }
                return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return emptyList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class TextEntriesAdapter(
    private val items: MutableList<String>,
    private val onTextChanged: (Int, String) -> Unit,
    private val onDeleteClicked: (Int) -> Unit
) : RecyclerView.Adapter<TextEntriesAdapter.ViewHolder>() {

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
        val currentPosition = holder.adapterPosition
        if (currentPosition == RecyclerView.NO_POSITION) return

        // Remove old watcher if any
        holder.textWatcher?.let { holder.editText.removeTextChangedListener(it) }

        // Bind text
        holder.editText.setText(items[currentPosition])

        // Set delete click
        holder.deleteButton.setOnClickListener {
            val latestPos = holder.adapterPosition
            if (latestPos != RecyclerView.NO_POSITION) {
                onDeleteClicked(latestPos)
            }
        }

        // Add text watcher
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val latestPos = holder.adapterPosition
                if (latestPos != RecyclerView.NO_POSITION) {
                    onTextChanged(latestPos, s?.toString() ?: "")
                }
            }
        }
        holder.editText.addTextChangedListener(watcher)
        holder.textWatcher = watcher

        // Autofocus newly added item
        if (currentPosition == newlyAddedPosition) {
            newlyAddedPosition = -1
            holder.editText.requestFocus()
            holder.editText.post {
                val imm = holder.editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(holder.editText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    override fun getItemCount(): Int = items.size
}