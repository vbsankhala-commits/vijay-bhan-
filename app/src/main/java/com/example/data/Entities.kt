package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employee_profile")
data class EmployeeProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val designation: String = "",
    val posting: String = "",
    val geminiApiKey: String = "",
    val cloudBackupEmail: String = "",
    val basicSalary: Int = 38000,
    val taCategory: String = "D"
)

@Entity(tableName = "tour_entries")
data class TourEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,            // YYYY-MM-DD
    val depTime: String,         // HH:MM (24-hour)
    val arrTime: String,         // HH:MM (24-hour)
    val travelMode: String,      // e.g. "M.T. Vehicle", "Govt. Vehicle", "Bus"
    val distance: Double,        // Distance in km
    val csNumber: String,        // Case Scene / Crime Scene Number
    val firNumber: String,       // F.I.R. Number
    val policeStation: String,   // Police Station Name (P.S.)
    val district: String,        // Related District
    val reportDate: String,      // YYYY-MM-DD
    val remarks: String = "",    // Extra description
    val monthYear: String,       // "YYYY-MM" (computed for simple grouping/filtering)
    val arrDate: String = ""     // YYYY-MM-DD (Date of arrival)
)

@Entity(tableName = "case_entries")
data class CaseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseNumber: String,        // Case Scene / C.S. No or Case Number
    val firNumber: String,         // F.I.R. Number
    val policeStation: String,     // P.S.
    val district: String,          // District
    val date: String,              // YYYY-MM-DD
    val tags: String = "",         // Comma-separated tags (keywords)
    val notes: String = "",        // Case details or synopsis
    val investigatingOfficer: String = "", // I.O. Name
    val status: String = "Active" // Active, Completed, Pending, etc.
)
