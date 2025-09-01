package com.example.myapplication

import androidx.room.*

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY timestamp DESC")
    suspend fun getAllPhotos(): List<PhotoEntity>

    @Insert
    suspend fun insertPhoto(photo: PhotoEntity)

    @Insert
    suspend fun insertPhotoMetadata(metadata: PhotoMetadataEntity)

    @Query("SELECT * FROM photo_metadata WHERE photoId = :photoId")
    suspend fun getPhotoMetadata(photoId: String): PhotoMetadataEntity?

    @Query("DELETE FROM photos WHERE id = :photoId")
    suspend fun deletePhoto(photoId: String)

    @Query("DELETE FROM photo_metadata WHERE photoId = :photoId")
    suspend fun deletePhotoMetadata(photoId: String)

    @Query("""
        SELECT p.*, m.latitude, m.longitude, m.address, m.compassDirection, m.deviceOrientation 
        FROM photos p 
        LEFT JOIN photo_metadata m ON p.id = m.photoId 
        ORDER BY p.timestamp DESC
    """)
    suspend fun getPhotosWithMetadata(): List<PhotoWithMetadata>
}

data class PhotoWithMetadata(
    val id: String,
    val filePath: String,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val address: String?,
    val compassDirection: String?,
    val deviceOrientation: String?
)