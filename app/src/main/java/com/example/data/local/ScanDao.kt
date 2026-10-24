package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_records ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE type = :type ORDER BY timestamp DESC")
    fun getScansByType(type: String): Flow<List<ScanRecord>>

    @Query("SELECT * FROM scan_records WHERE id = :id LIMIT 1")
    suspend fun getScanById(id: Long): ScanRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanRecord): Long

    @Query("DELETE FROM scan_records WHERE id = :id")
    suspend fun deleteScanById(id: Long)

    @Query("DELETE FROM scan_records")
    suspend fun clearAllScans()

    @Query("SELECT COUNT(*) FROM scan_records")
    fun getTotalScanCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scan_records WHERE threatLevel IN ('HIGH', 'CRITICAL')")
    fun getThreatCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scan_records WHERE threatLevel = 'SAFE'")
    fun getSafeCount(): Flow<Int>
}
