package com.freelapp.app.ui.main

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.freelapp.app.R
import com.freelapp.libs.locationfetcher.locationFetcher
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.seconds

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val locationFetcher by lazy {
        requireContext().applicationContext.locationFetcher(this, "Rationale") {
            debug = true
            interval = 5.seconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        // TODO: Use the ViewModel
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val message: TextView = view.findViewById(R.id.message)
        locationFetcher.location
            .onEach { message.text = it.toString() }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

}