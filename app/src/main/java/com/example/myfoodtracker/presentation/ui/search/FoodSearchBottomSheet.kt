package com.example.myfoodtracker.presentation.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.myfoodtracker.R
import com.example.myfoodtracker.databinding.FragmentFoodSearchBottomSheetBinding
import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.presentation.viewmodel.FoodSearchViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koin.androidx.viewmodel.ext.android.viewModel

class FoodSearchBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onFoodSelected(food: FoodItem)
        fun onQuickAddRequested()
    }

    private var _binding: FragmentFoodSearchBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FoodSearchViewModel by viewModel()
    private lateinit var adapter: FoodSearchAdapter
    private var activeDate: String = ""
    private var lastQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeDate = arguments?.getString(ARG_ACTIVE_DATE).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoodSearchBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FoodSearchAdapter { food ->
            hideKeyboard()
            getListener()?.onFoodSelected(food)
            dismiss()
        }
        binding.rvSearchResults.adapter = adapter

        viewModel.results.observe(viewLifecycleOwner) { results ->
            renderResults(results, lastQuery)
        }

        binding.etFoodSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                lastQuery = s?.toString().orEmpty()
                viewModel.search(lastQuery, activeDate)
            }
        })

        binding.btnCreateCustomFood.setOnClickListener {
            // Story 3.3 entry point: surface only, creation is out of scope.
            Toast.makeText(
                requireContext(),
                getString(R.string.food_search_create_custom_food),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnSearchQuickAdd.setOnClickListener {
            hideKeyboard()
            getListener()?.onQuickAddRequested()
            dismiss()
        }

        binding.etFoodSearch.requestFocus()
        if (savedInstanceState == null) {
            viewModel.loadInitial(activeDate)
        }
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etFoodSearch.windowToken, 0)
    }

    private fun getListener(): Listener? =
        parentFragment as? Listener ?: activity as? Listener

    private fun renderResults(results: List<FoodItem>, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            binding.tvRecentTitle.visibility = View.VISIBLE
            binding.tvEmptyResults.visibility = View.GONE
            binding.btnCreateCustomFood.visibility = View.GONE
            binding.rvSearchResults.visibility = View.VISIBLE
            adapter.submitList(results)
            return
        }
        binding.tvRecentTitle.visibility = View.GONE
        if (results.isEmpty()) {
            binding.rvSearchResults.visibility = View.GONE
            binding.tvEmptyResults.visibility = View.VISIBLE
            binding.tvEmptyResults.text = getString(R.string.food_search_no_results_format, trimmed)
            binding.btnCreateCustomFood.visibility = View.VISIBLE
        } else {
            binding.rvSearchResults.visibility = View.VISIBLE
            binding.tvEmptyResults.visibility = View.GONE
            binding.btnCreateCustomFood.visibility = View.GONE
            adapter.submitList(results)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ACTIVE_DATE = "active_date"

        fun newInstance(activeDate: String): FoodSearchBottomSheet {
            return FoodSearchBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_ACTIVE_DATE, activeDate) }
            }
        }
    }
}
