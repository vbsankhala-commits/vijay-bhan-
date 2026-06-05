package com.example.utils

import com.example.data.EmployeeProfile
import com.example.data.TourEntry
import java.util.Locale

object RajasthanTaRules {

    /**
     * Determines Rajasthan Civil Services Category based on Basic Salary Pay as per the official table.
     * Category क (A): 37000 and above
     * Category ख (B): 19000 to 36999
     * Category ग (C): 15000 to 18999
     * Category घ (D): 10000 to 14999
     * Category ड़ (E): 0 to 9999
     */
    fun getCategoryBySalary(salary: Int): String {
        return when {
            salary >= 37000 -> "A"
            salary >= 19000 -> "B"
            salary >= 15000 -> "C"
            salary >= 10000 -> "D"
            else -> "E"
        }
    }

    /**
     * Gets incidental charges per km based on category as per Column 6:
     * - A (क): 7 paise (0.07 ₹)
     * - B (ख): 5 paise (0.05 ₹)
     * - C (ग): 3 paise (0.03 ₹)
     * - D (घ): 3 paise (0.03 ₹)
     * - E (ड़): 3 paise (0.03 ₹)
     */
    fun getIncidentalChargePerKm(category: String): Double {
        return when (category.trim().uppercase(Locale.getDefault())) {
            "A" -> 0.07
            "B" -> 0.05
            "C" -> 0.03
            "D" -> 0.03
            "E" -> 0.03
            else -> 0.03
        }
    }

    /**
     * Gets full daily allowance (DA) rate based on category grade and target location.
     * As per departmental rules:
     * - Outside Jaipur (State Standard):
     *   A -> 135, B -> 120, C -> 105, D -> 90, E -> 55
     * - Inside Jaipur (State Capital):
     *   A -> 170, B -> 150, C -> 130, D -> 110, E -> 70
     * - Metros (New Delhi, Mumbai, Kolkata, Chennai):
     *   A -> 260, B -> 230, C -> 200, D -> 170, E -> 105
     */
    fun getFullDaRate(category: String, destination: String = ""): Double {
        val dest = destination.lowercase(Locale.getDefault())
        val cat = category.trim().uppercase(Locale.getDefault())
        return when {
            dest.contains("delhi") || dest.contains("mumbai") || dest.contains("kolkata") || dest.contains("chennai") || dest.contains("new delhi") -> {
                when (cat) {
                    "A" -> 260.0
                    "B" -> 230.0
                    "C" -> 200.0
                    "D" -> 170.0
                    "E" -> 105.0
                    else -> 105.0
                }
            }
            dest.contains("jaipur") -> {
                when (cat) {
                    "A" -> 170.0
                    "B" -> 150.0
                    "C" -> 130.0
                    "D" -> 110.0
                    "E" -> 70.0
                    else -> 70.0
                }
            }
            else -> {
                when (cat) {
                    "A" -> 135.0
                    "B" -> 120.0
                    "C" -> 105.0
                    "D" -> 90.0
                    "E" -> 55.0
                    else -> 55.0
                }
            }
        }
    }

    /**
     * Estimates mileage rate per km based on travel mode under Rajasthan Rules.
     * Road travel by private/taxi is typically reimbursed at Rs. 10 - 12 per Km.
     */
    fun getMileageRate(travelMode: String): Double {
        val mode = travelMode.lowercase(Locale.getDefault())
        return when {
            mode.contains("car") || mode.contains("taxi") -> 12.0
            mode.contains("motorcycle") || mode.contains("bike") || mode.contains("m.t. vehicle") || mode.contains("mt vehicle") -> 10.0 // Private vehicle
            mode.contains("bus") -> 3.0 // Public bus rate reference
            mode.contains("govt") -> 0.0 // Government vehicle has no separate mileage allowance
            else -> 10.0 // Default road mileage
        }
    }

    /**
     * Estimates the DA percentage based on elapsed travel hours for a single day.
     * Footnote 2 Rule:
     * - Under 6 hours: 0% ("6 घंटे अनुपस्थिति के लिए शून्य")
     * - 6 to 12 hours: 50% ("6 से अधिक 12 घंटे तक के लिए 50%")
     * - Over 12 hours: 100% ("12 घंटे से अधिक के लिए पूर्ण")
     */
    fun getDaPercentage(startHour24: String, endHour24: String): Double {
        return try {
            val startParts = startHour24.split(":")
            val endParts = endHour24.split(":")
            val startMins = startParts[0].trim().toInt() * 60 + startParts[1].trim().toInt()
            val endMins = endParts[0].trim().toInt() * 60 + endParts[1].trim().toInt()
            
            val diffMins = if (endMins >= startMins) endMins - startMins else (1440 - startMins) + endMins
            val diffHours = diffMins / 60.0

            when {
                diffHours < 6.0 -> 0.0
                diffHours <= 12.0 -> 0.50
                else -> 1.00
            }
        } catch (e: Exception) {
            0.50 // Same standard fallback matching 6-12 hours average trip
        }
    }

    /**
     * Calculates the estimated total TA and DA allowances for a specific tour entry.
     */
    fun calculateTripAllowance(profile: EmployeeProfile, entry: TourEntry): Pair<Double, Double> {
        val category = getCategoryBySalary(profile.basicSalary)
        val mileageRate = getMileageRate(entry.travelMode)
        val distanceMultiplier = if (entry.travelMode.lowercase(Locale.getDefault()).contains("govt")) 0.0 else 1.0
        
        // TA is travel distance * mileage rate plus incidental charge per km if private
        val baseTaRate = mileageRate + getIncidentalChargePerKm(category)
        val ta = entry.distance * baseTaRate * distanceMultiplier
        
        val daRate = getFullDaRate(category, entry.district + " " + entry.policeStation)
        val daPercentage = getDaPercentage(entry.depTime, entry.arrTime)
        val da = daRate * daPercentage
        
        return Pair(ta, da)
    }
}
