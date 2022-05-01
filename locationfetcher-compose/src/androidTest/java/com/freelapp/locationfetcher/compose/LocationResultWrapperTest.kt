package com.freelapp.locationfetcher.compose

import android.location.Location
import androidx.test.filters.SmallTest
import com.google.android.gms.location.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

@SmallTest
internal class LocationResultWrapperTest {

    @Test
    fun testEqualityOfLocationWrapper() {
        val time = System.currentTimeMillis()
        val location1 = Location("test").apply {
            latitude = 12.0
            longitude = 12.5
            this.time = time
        }
        val location2 = Location("test").apply {
            latitude = 12.0
            longitude = 12.5
            this.time = time
        }
        val locationResult1 = LocationResult.create(listOf(location1))
        val locationResult2 = LocationResult.create(listOf(location2))
        assertEquals(locationResult1, locationResult2)
        val locationRequest1 = LocationResultWrapper(locationResult1)
        val locationRequest2 = LocationResultWrapper(locationResult2)
        assertEquals(locationRequest1, locationRequest2)
    }

    @Test
    fun testInequalityOfLocationWrapper() {
        val time1 = System.currentTimeMillis()
        val time2 = time1 + 1000
        assertNotEquals(time1, time2)
        val location1 = Location("test").apply {
            latitude = 12.0
            longitude = 12.5
            time = time1
        }
        val location2 = Location("test").apply {
            latitude = 12.0
            longitude = 12.5
            time = time2
        }
        val locationResult1 = LocationResult.create(listOf(location1))
        val locationResult2 = LocationResult.create(listOf(location2))
        assertNotEquals(locationResult1, locationResult2)
        val locationResultWrapper1 = LocationResultWrapper(locationResult1)
        val locationResultWrapper2 = LocationResultWrapper(locationResult2)
        assertNotEquals(locationResultWrapper1, locationResultWrapper2)
    }
}
