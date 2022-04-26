package com.freelapp.app.ui.main

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.freelapp.app.R
import com.freelapp.libs.locationfetcher.locationFetcher
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val TAG = "MainFragment"

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val locationFetcher1 = locationFetcher("Rationale") {
        debug = true
        interval = 15.seconds
        fastestInterval = 15.seconds
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }
    private val locationFetcher2 = locationFetcher("Rationale") {
        debug = true
        interval = 5.seconds
        fastestInterval = 5.seconds
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
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
        val job0 = locationFetcher1.location
            .onEach { Log.d(TAG, "onViewCreated 1: $it") }
            .launchIn(viewLifecycleOwner.lifecycleScope)
        val job = locationFetcher2.location
            .onEach { Log.d(TAG, "onViewCreated 2: $it") }
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewLifecycleOwner.lifecycleScope.launch {
            delay(10.seconds)
            job0.cancel()
            delay(20.seconds)
            job.cancel()
            delay(40.seconds)
            locationFetcher2.location
                .onEach { Log.d(TAG, "onViewCreated 2.2: $it") }
                .launchIn(viewLifecycleOwner.lifecycleScope)
        }

    }

}