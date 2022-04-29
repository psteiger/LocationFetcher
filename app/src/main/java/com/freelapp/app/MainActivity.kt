package com.freelapp.app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import com.freelapp.app.ui.main.MainFragment
import com.freelapp.locationfetcher.compose.LocalLocationFetcher
import com.freelapp.locationfetcher.compose.LocationFetcher
import com.google.android.gms.location.LocationRequest
import kotlin.time.Duration.Companion.seconds

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocationFetcher(
                requestConfig = {
                    interval = 15.seconds.inWholeMilliseconds
                    fastestInterval = 15.seconds.inWholeMilliseconds
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                },
                rationale = "We need your location for searching restaurants nearby."
            ) {
                val location = LocalLocationFetcher.current
                Log.d(TAG, "onCreate: $location")
            }
        }
//        setContentView(R.layout.main_activity)
//        if (savedInstanceState == null) {
//            supportFragmentManager.beginTransaction()
//                .replace(R.id.container, MainFragment.newInstance())
//                .commitNow()
//        }
    }
}