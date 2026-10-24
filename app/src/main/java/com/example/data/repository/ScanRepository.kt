package com.example.data.repository

import com.example.data.local.ScanDao
import com.example.data.local.ScanRecord
import kotlinx.coroutines.flow.Flow

class ScanRepository(private val scanDao: ScanDao) {
    val allScans: Flow<List<ScanRecord>> = scanDao.getAllScans()
    val totalCount: Flow<Int> = scanDao.getTotalScanCount()
    val threatCount: Flow<Int> = scanDao.getThreatCount()
    val safeCount: Flow<Int> = scanDao.getSafeCount()

    fun getScansByType(type: String): Flow<List<ScanRecord>> = scanDao.getScansByType(type)

    suspend fun getScanById(id: Long): ScanRecord? = scanDao.getScanById(id)

    suspend fun insertScan(scan: ScanRecord): Long = scanDao.insertScan(scan)

    suspend fun deleteScan(id: Long) = scanDao.deleteScanById(id)

    suspend fun clearAll() = scanDao.clearAllScans()
}
