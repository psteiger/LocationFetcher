package com.freelapp.locationfetcher.compose

import com.google.android.gms.location.LocationRequest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

internal class LocationRequestWrapperTest {

    @Test
    fun `test equality of LocationRequestWrapper`() {
        val locationRequest1 = LocationRequestWrapper(
            LocationRequest.create().apply {
                interval = 5.seconds.inWholeMilliseconds
            }
        )
        val locationRequest2 = LocationRequestWrapper(
            LocationRequest.create().apply {
                interval = 5.seconds.inWholeMilliseconds
            }
        )
        assert(locationRequest1 == locationRequest2) {
            "Equality test failed"
        }
    }

    @Test
    fun `test inequality of LocationRequestWrapper`() {
        val locationRequest1 = LocationRequestWrapper(
            LocationRequest.create().apply {
                interval = 5.seconds.inWholeMilliseconds
            }
        )
        val locationRequest2 = LocationRequestWrapper(
            LocationRequest.create().apply {
                interval = 10.seconds.inWholeMilliseconds
            }
        )
        assert(locationRequest1 != locationRequest2) {
            "Equality test failed"
        }
    }
}