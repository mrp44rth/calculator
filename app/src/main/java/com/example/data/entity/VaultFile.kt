package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_files")
data class VaultFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val filePath: String,
    val originalName: String?,
    val originalPath: String?,
    val mimeType: String,
    val isVideo: Boolean,
    val size: Long,
    val timestamp: Long = System.currentTimeMillis()
)
