package com.example.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val timestamp: Long
)

@Entity(tableName = "photo_metadata")
data class PhotoMetadataEntity(
    @PrimaryKey val photoId: String,
    val latitude: Double?,
    val longitude: Double?,
    val address: String?,
    val compassDirection: String,
    val deviceOrientation: String,
    val timestamp: Long
)