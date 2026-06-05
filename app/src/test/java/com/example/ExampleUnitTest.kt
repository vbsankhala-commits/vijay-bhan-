package com.example

import com.example.utils.RajasthanTaRules
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests to verify correct TA/DA rate calculations as per uploaded Rajasthan Civil Services tables.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCategoryBySalary() {
    // क (A): 37000 and above
    assertEquals("A", RajasthanTaRules.getCategoryBySalary(42000))
    assertEquals("A", RajasthanTaRules.getCategoryBySalary(37000))
    
    // ख (B): 19000 to 36999
    assertEquals("B", RajasthanTaRules.getCategoryBySalary(35000))
    assertEquals("B", RajasthanTaRules.getCategoryBySalary(19000))
    
    // ग (C): 15000 to 18999
    assertEquals("C", RajasthanTaRules.getCategoryBySalary(17500))
    assertEquals("C", RajasthanTaRules.getCategoryBySalary(15000))
    
    // घ (D): 10000 to 14999
    assertEquals("D", RajasthanTaRules.getCategoryBySalary(12000))
    assertEquals("D", RajasthanTaRules.getCategoryBySalary(10000))
    
    // ड़ (E): 0 to 9999
    assertEquals("E", RajasthanTaRules.getCategoryBySalary(8500))
    assertEquals("E", RajasthanTaRules.getCategoryBySalary(4500))
  }

  @Test
  fun testFullDaRates() {
    // Outside Jaipur (Standard rate within state)
    assertEquals(135.0, RajasthanTaRules.getFullDaRate("A", "Ajmer"), 0.0)
    assertEquals(120.0, RajasthanTaRules.getFullDaRate("B", "Jodhpur"), 0.0)
    assertEquals(105.0, RajasthanTaRules.getFullDaRate("C", "Kota"), 0.0)
    assertEquals(90.0, RajasthanTaRules.getFullDaRate("D", "Udaipur"), 0.0)
    assertEquals(55.0, RajasthanTaRules.getFullDaRate("E", "Bikaner"), 0.0)

    // Inside Jaipur
    assertEquals(170.0, RajasthanTaRules.getFullDaRate("A", "Jaipur Junction"), 0.0)
    assertEquals(150.0, RajasthanTaRules.getFullDaRate("B", "Jaipur West PS"), 0.0)
    assertEquals(130.0, RajasthanTaRules.getFullDaRate("C", "Jaipur Rural"), 0.0)
    assertEquals(110.0, RajasthanTaRules.getFullDaRate("D", "Jaipur City"), 0.0)
    assertEquals(70.0, RajasthanTaRules.getFullDaRate("E", "Jaipur East"), 0.0)

    // Metro locations (New Delhi, Mumbai, Kolkata, Chennai)
    assertEquals(260.0, RajasthanTaRules.getFullDaRate("A", "New Delhi Station"), 0.0)
    assertEquals(230.0, RajasthanTaRules.getFullDaRate("B", "Mumbai Central"), 0.0)
    assertEquals(200.0, RajasthanTaRules.getFullDaRate("C", "Kolkata HQ"), 0.0)
    assertEquals(170.0, RajasthanTaRules.getFullDaRate("D", "Chennai Port"), 0.0)
    assertEquals(105.0, RajasthanTaRules.getFullDaRate("E", "Delhi Metro"), 0.0)
  }

  @Test
  fun testDaPercentage() {
    // Under 6 hours -> 0%
    assertEquals(0.0, RajasthanTaRules.getDaPercentage("10:00", "15:30"), 0.0) // 5.5 hours -> 0%
    assertEquals(0.0, RajasthanTaRules.getDaPercentage("10:00", "15:59"), 0.0) // 5 hours 59 mins -> 0%

    // 6 to 12 hours -> 50%
    assertEquals(0.50, RajasthanTaRules.getDaPercentage("10:00", "16:00"), 0.0) // Exactly 6 hours -> 50%
    assertEquals(0.50, RajasthanTaRules.getDaPercentage("10:00", "22:00"), 0.0) // Exactly 12 hours -> 50%
    assertEquals(0.50, RajasthanTaRules.getDaPercentage("08:00", "18:30"), 0.0) // 10.5 hours -> 50%

    // Over 12 hours -> 100%
    assertEquals(1.00, RajasthanTaRules.getDaPercentage("08:00", "20:01"), 0.0) // 12 hours 1 min -> 100%
    assertEquals(1.00, RajasthanTaRules.getDaPercentage("06:00", "22:00"), 0.0) // 16 hours -> 100%
  }

  @Test
  fun testIncidentalCharges() {
    assertEquals(0.07, RajasthanTaRules.getIncidentalChargePerKm("A"), 0.0)
    assertEquals(0.05, RajasthanTaRules.getIncidentalChargePerKm("B"), 0.0)
    assertEquals(0.03, RajasthanTaRules.getIncidentalChargePerKm("C"), 0.0)
    assertEquals(0.03, RajasthanTaRules.getIncidentalChargePerKm("D"), 0.0)
    assertEquals(0.03, RajasthanTaRules.getIncidentalChargePerKm("E"), 0.0)
  }

  @Test
  fun testGoogleMapsRouteDistance() {
    val distance = com.example.utils.RajasthanPoliceHelper.getGoogleMapsDistance("Jhunjhunu", listOf("Udaipurwati", "Nawalgarh"))
    // Udaipurwati (45km) + Nawalgarh (based on same-district hash layout) + return leg
    assertTrue(distance > 50.0)
  }
}
