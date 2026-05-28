package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TourDao {
    @Query("SELECT * FROM employee_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<EmployeeProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: EmployeeProfile)

    @Query("SELECT * FROM tour_entries ORDER BY date ASC, depTime ASC")
    fun getAllTourEntries(): Flow<List<TourEntry>>

    @Query("SELECT * FROM tour_entries WHERE monthYear = :monthYear ORDER BY date ASC, depTime ASC")
    fun getTourEntriesForMonth(monthYear: String): Flow<List<TourEntry>>

    @Query("SELECT DISTINCT monthYear FROM tour_entries ORDER BY monthYear DESC")
    fun getMonthsWithEntries(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTourEntry(entry: TourEntry)

    @Delete
    suspend fun deleteTourEntry(entry: TourEntry)

    @Query("SELECT * FROM tour_entries WHERE id = :id LIMIT 1")
    suspend fun getTourEntryById(id: Long): TourEntry?

    // Case Entry DB Operations
    @Query("SELECT * FROM case_entries ORDER BY date DESC, id DESC")
    fun getAllCaseEntries(): Flow<List<CaseEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCaseEntry(case: CaseEntry)

    @Delete
    suspend fun deleteCaseEntry(case: CaseEntry)

    @Query("SELECT * FROM case_entries WHERE id = :id LIMIT 1")
    suspend fun getCaseEntryById(id: Long): CaseEntry?
}
