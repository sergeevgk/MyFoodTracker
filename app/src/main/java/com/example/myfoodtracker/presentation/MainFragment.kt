package com.example.myfoodtracker.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myfoodtracker.databinding.FragmentFirstBinding
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MealViewModel by viewModel()
    private lateinit var adapter: MealEntriesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize adapter
        adapter = MealEntriesAdapter(
            onTitleChanged = { id, newTitle ->
                viewModel.updateMealEntry(id, newTitle)
            },
            onDeleteClicked = { id ->
                viewModel.deleteMealEntry(id)
            }
        )

        binding.recyclerViewEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewEntries.adapter = adapter

        // Setup add button
        binding.buttonAdd.setOnClickListener {
            viewModel.addMealEntry()
        }

        // Observe ViewModel state
        viewModel.mealEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
        }

        viewModel.newlyAddedPosition.observe(viewLifecycleOwner) { position ->
            if (position != null) {
                adapter.setNewlyAddedPosition(position)
                binding.recyclerViewEntries.scrollToPosition(position)
                viewModel.consumeNewlyAddedPosition()
            }
        }

        // Load data initial
        viewModel.loadMealEntries()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
