package com.example.data.dao

import androidx.room.*
import com.example.data.entity.VaultFile
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultFileDao {
    @Query("SELECT * FROM vault_files ORDER BY timestamp DESC")
    fun getAllFiles(): Flow<List<VaultFile>>

    @Query("SELECT * FROM vault_files WHERE isVideo = :isVideo ORDER BY timestamp DESC")
    fun getFilesByType(isVideo: Boolean): Flow<List<VaultFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: VaultFile)

    @Delete
    suspend fun deleteFile(file: VaultFile)

    @Query("DELETE FROM vault_files WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM vault_files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: Int): VaultFile?

    @Query("DELETE FROM vault_files")
    suspend fun clearAll()
}
