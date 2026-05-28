package com.example.data

import kotlinx.coroutines.flow.Flow

class TourRepository(private val tourDao: TourDao) {
    val profile: Flow<EmployeeProfile?> = tourDao.getProfile()
    val allTourEntries: Flow<List<TourEntry>> = tourDao.getAllTourEntries()
    val availableMonths: Flow<List<String>> = tourDao.getMonthsWithEntries()

    suspend fun saveProfile(profile: EmployeeProfile) {
        tourDao.insertProfile(profile)
    }

    fun getTourEntriesForMonth(monthYear: String): Flow<List<TourEntry>> {
        return tourDao.getTourEntriesForMonth(monthYear)
    }

    suspend fun saveTourEntry(entry: TourEntry) {
        tourDao.insertTourEntry(entry)
    }

    suspend fun deleteTourEntry(entry: TourEntry) {
        tourDao.deleteTourEntry(entry)
    }

    suspend fun getTourEntryById(id: Long): TourEntry? {
        return tourDao.getTourEntryById(id)
    }

    // Case Entry DB Actions
    val allCaseEntries: Flow<List<CaseEntry>> = tourDao.getAllCaseEntries()

    suspend fun saveCaseEntry(case: CaseEntry) {
        tourDao.insertCaseEntry(case)
    }

    suspend fun deleteCaseEntry(case: CaseEntry) {
        tourDao.deleteCaseEntry(case)
    }

    suspend fun getCaseEntryById(id: Long): CaseEntry? {
        return tourDao.getCaseEntryById(id)
    }
}
