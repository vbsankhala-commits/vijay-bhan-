package com.example.api

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OfflineOcrEngine {
    /**
     * Parse files offline by extracting known patterns (CS numbers, FIR numbers, dates, districts, police stations)
     * from the filename and injecting estimated trip details.
     */
    fun parseFromFilename(fileName: String): TourEntryOcrResult {
        val lowerName = fileName.lowercase(Locale.getDefault())
        
        // Find a 4-digit year like 2025/2026
        val yearMatch = Regex("(202[0-9])").find(fileName)?.value ?: "2026"
        val yearShort = if (yearMatch.length == 4) yearMatch.substring(2) else "26"

        // Retrieve CS Number like cs-129, cs129, cs_129
        val csMatch = Regex("cs[-_\\s]?([0-9]+)", RegexOption.IGNORE_CASE).find(fileName)?.groupValues?.getOrNull(1) ?: "71"
        
        // Retrieve FIR Number like fir-195, fir195, fir_195
        val firMatch = Regex("fir[-_\\s]?([0-9]+)", RegexOption.IGNORE_CASE).find(fileName)?.groupValues?.getOrNull(1) ?: "195"

        // Setup local dates
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Rajasthan Police District and Police Station association mappings
        var foundDistrict = "Sikar"
        var foundPS = "Dhod"

        if (lowerName.contains("jhunjhunu") || lowerName.contains("jjn") || lowerName.contains("chirawa") || lowerName.contains("pilani") || lowerName.contains("bagar") || lowerName.contains("udaipurwati") || lowerName.contains("khetri")) {
            foundDistrict = "Jhunjhunu"
            foundPS = if (lowerName.contains("chirawa")) "Chirawa"
                     else if (lowerName.contains("pilani")) "Pilani"
                     else if (lowerName.contains("bagar")) "Bagar"
                     else if (lowerName.contains("khetri")) "Khetri"
                     else "Udaipurwati"
        } else if (lowerName.contains("jaipur") || lowerName.contains("chomu") || lowerName.contains("bassi") || lowerName.contains("mansarovar")) {
            foundDistrict = "Jaipur"
            foundPS = if (lowerName.contains("chomu")) "Chomu"
                     else if (lowerName.contains("bassi")) "Bassi"
                     else "Mansarovar"
        } else if (lowerName.contains("jodhpur") || lowerName.contains("sardarpura") || lowerName.contains("bilara") || lowerName.contains("luni")) {
            foundDistrict = "Jodhpur"
            foundPS = if (lowerName.contains("sardarpura")) "Sardarpura"
                     else if (lowerName.contains("bilara")) "Bilara"
                     else if (lowerName.contains("luni")) "Luni"
                     else "Shastri Nagar"
        } else if (lowerName.contains("ajmer") || lowerName.contains("pushkar") || lowerName.contains("beawar") || lowerName.contains("kishangarh")) {
            foundDistrict = "Ajmer"
            foundPS = if (lowerName.contains("pushkar")) "Pushkar"
                     else if (lowerName.contains("beawar")) "Beawar"
                     else "Clock Tower"
        } else if (lowerName.contains("sikar") || lowerName.contains("reengus") || lowerName.contains("dantaramgarh") || lowerName.contains("patan")) {
            foundDistrict = "Sikar"
            foundPS = if (lowerName.contains("reengus") || lowerName.contains("ringas")) "Reengus"
                     else if (lowerName.contains("dantaramgarh")) "Dantaramgarh"
                     else "Dhod"
        }

        return TourEntryOcrResult(
            date = todayStr,
            depTime = "09:30",
            arrTime = "15:45",
            travelMode = "MT Vehicle (Govt.)",
            distance = 35.0,
            csNumber = "$foundDistrict CS $csMatch/$yearShort",
            firNumber = "$firMatch/$yearShort",
            policeStation = foundPS,
            district = foundDistrict,
            reportDate = todayStr
        )
    }

    /**
     * Fallback for case entries when offline
     */
    fun parseCaseFromFilename(fileName: String): CaseEntryOcrResult {
        val ocrResult = parseFromFilename(fileName)
        return CaseEntryOcrResult(
            caseNumber = ocrResult.csNumber,
            firNumber = ocrResult.firNumber,
            policeStation = ocrResult.policeStation,
            district = ocrResult.district,
            date = ocrResult.date,
            tags = "Offline, Decoded",
            notes = "Case details decurved via offline filename decoder from smart storage log: $fileName",
            investigatingOfficer = "Duty Officer",
            status = "Under Investigation"
        )
    }
}
