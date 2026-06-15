package com.example.data.repository

import com.example.data.dao.VaultFileDao
import com.example.data.entity.VaultFile
import kotlinx.coroutines.flow.Flow

class VaultRepository(private val vaultFileDao: VaultFileDao) {
    val allFiles: Flow<List<VaultFile>> = vaultFileDao.getAllFiles()

    fun getFilesByType(isVideo: Boolean): Flow<List<VaultFile>> {
        return vaultFileDao.getFilesByType(isVideo)
    }

    suspend fun insert(vaultFile: VaultFile) {
        vaultFileDao.insertFile(vaultFile)
    }

    suspend fun delete(vaultFile: VaultFile) {
        vaultFileDao.deleteFile(vaultFile)
    }

    suspend fun deleteById(id: Int) {
        vaultFileDao.deleteById(id)
    }

    suspend fun getFileById(id: Int): VaultFile? {
        return vaultFileDao.getFileById(id)
    }

    suspend fun clearAll() {
        vaultFileDao.clearAll()
    }
}
