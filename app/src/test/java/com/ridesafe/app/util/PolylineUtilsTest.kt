package com.ridesafe.app.util

import com.ridesafe.app.data.model.TripInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint

class PolylineUtilsTest {

    @Test
    fun testPolylineEncodeAndDecodeRoundtrip() {
        val originalPoints = listOf(
            GeoPoint(28.6139, 77.2090),
            GeoPoint(28.6200, 77.2150),
            GeoPoint(28.6300, 77.2250)
        )

        val encoded = PolylineUtils.encodePolyline(originalPoints)
        assertTrue("Encoded polyline should not be empty", encoded.isNotBlank())

        val decoded = PolylineUtils.decodePolyline(encoded)
        assertEquals("Decoded points count must match original", originalPoints.size, decoded.size)

        for (i in originalPoints.indices) {
            assertEquals(originalPoints[i].latitude, decoded[i].latitude, 0.0001)
            assertEquals(originalPoints[i].longitude, decoded[i].longitude, 0.0001)
        }
    }

    @Test
    fun testDecodeKnownOsrmPolyline() {
        // Known polyline from OSRM demo server for a short segment in Berlin
        val osrmPolyline = "ifp_Ic_vpA??ZEA_@?GGyBCeAEaBAOAW?YAGEoBGgBEaBAIE_B?GAKCICICMAKAI?EAG?OIcCGsCAQA_@E@"
        val points = PolylineUtils.decodePolyline(osrmPolyline)

        assertTrue("Should decode at least one point", points.isNotEmpty())
        // Start near 52.5170, 13.3888
        assertEquals(52.5170, points.first().latitude, 0.01)
        assertEquals(13.3888, points.first().longitude, 0.01)
    }

    @Test
    fun testFormatDistance() {
        assertEquals("0 km", PolylineUtils.formatDistance(0.0))
        assertEquals("450 m", PolylineUtils.formatDistance(450.0))
        assertEquals("1.5 km", PolylineUtils.formatDistance(1500.0))
        assertEquals("45 km", PolylineUtils.formatDistance(45200.0))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("0 min", PolylineUtils.formatDuration(0.0))
        assertEquals("35 min", PolylineUtils.formatDuration(2100.0))
        assertEquals("1 hr", PolylineUtils.formatDuration(3600.0))
        assertEquals("1 hr 15 min", PolylineUtils.formatDuration(4500.0))
        assertEquals("2 hr 30 min", PolylineUtils.formatDuration(9000.0))
    }

    @Test
    fun testTripInfoPlannedState() {
        val noTrip = TripInfo()
        assertFalse("Default trip should not be planned", noTrip.isTripPlanned)

        val plannedTrip = TripInfo(
            startName = "India Gate",
            startLat = 28.6129,
            startLng = 77.2295,
            destName = "Red Fort",
            destLat = 28.6562,
            destLng = 77.2410,
            routeGeometry = "encoded_polyline_here",
            distanceMeters = 6500.0,
            durationSeconds = 1200.0
        )
        assertTrue("Trip with valid start, dest, and geometry should be planned", plannedTrip.isTripPlanned)
        assertEquals("6.5 km", plannedTrip.formattedDistance)
        assertEquals("20 min", plannedTrip.formattedDuration)
    }

    @Test
    fun testWebTripInfoCompatibility() {
        // Simulates tripInfo created on the Web app
        val webTrip = TripInfo(
            startName = "India Gate",
            destName = "Red Fort",
            startLat = 28.6129,
            startLng = 77.2295,
            destLat = 28.6562,
            destLng = 77.2410,
            encodedPolyline = "ifp_Ic_vpA??ZEA_@?GGyBCeAEaBAOAW?YAGEoBGgBEaBAIE_B?GAKCICICMAKAI?EAG?OIcCGsCAQA_@E@",
            distanceKm = 6.5,
            durationMin = 20.0,
            hasPlannedTrip = true
        )

        assertTrue("Web trip should be recognized as planned", webTrip.isTripPlanned)
        assertEquals("ifp_Ic_vpA??ZEA_@?GGyBCeAEaBAOAW?YAGEoBGgBEaBAIE_B?GAKCICICMAKAI?EAG?OIcCGsCAQA_@E@", webTrip.effectiveGeometry)
        assertEquals("6.5 km", webTrip.formattedDistance)
        assertEquals("20 min", webTrip.formattedDuration)

        val decoded = PolylineUtils.decodeGeometry(webTrip.effectiveGeometry)
        assertTrue("Effective geometry should decode into points", decoded.isNotEmpty())
    }

    @Test
    fun testDecodeGeometryWithQuotesAndEscapes() {
        val rawPolyline = "ifp_Ic_vpA??ZEA_@?GGyBCeAEaBAOAW?YAGEoBGgBEaBAIE_B?GAKCICICMAKAI?EAG?OIcCGsCAQA_@E@"
        val quoted = "\"$rawPolyline\""
        val decodedQuoted = PolylineUtils.decodeGeometry(quoted)
        assertTrue("Quoted polyline should decode points", decodedQuoted.isNotEmpty())
        assertEquals(PolylineUtils.decodePolyline(rawPolyline).size, decodedQuoted.size)
    }

    @Test
    fun testBoundingBoxCalculation() {
        val points = listOf(
            GeoPoint(28.0, 77.0),
            GeoPoint(29.0, 78.0)
        )
        val box = PolylineUtils.calculateRouteBoundingBox(points)
        assertNotNull("Bounding box should not be null", box)
        assertTrue("North boundary should enclose max latitude", box!!.latNorth >= 29.0)
        assertTrue("South boundary should enclose min latitude", box.latSouth <= 28.0)
        assertTrue("East boundary should enclose max longitude", box.lonEast >= 78.0)
        assertTrue("West boundary should enclose min longitude", box.lonWest <= 77.0)
    }
}

