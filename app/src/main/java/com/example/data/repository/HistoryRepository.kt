package com.example.data.repository

import com.example.data.dao.HistoryDao
import com.example.data.entity.CalculationHistory
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val history: Flow<List<CalculationHistory>> = historyDao.getHistory()

    suspend fun insert(historyItem: CalculationHistory) {
        historyDao.insert(historyItem)
    }

    suspend fun delete(id: Int) {
        historyDao.delete(id)
    }

    suspend fun clearAll() {
        historyDao.clearAll()
    }
}
