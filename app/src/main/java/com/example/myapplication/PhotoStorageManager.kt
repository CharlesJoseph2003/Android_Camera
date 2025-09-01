package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.StatFs
import android.util.Log
import androidx.activity.result.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID

data class PhotoReference(
    val id: String,
    val optimizedPath: String,
    val thumbnailPath: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PhotoStorageManager(private val context: Context) {
    private val database = PhotoDatabase.getDatabase(context)
    private val photoDao = database.photoDao()

    private val localCacheDir = File(context.cacheDir, "photos").apply {
        if (!exists()) mkdirs()
    }

    private val localStorageDir = File(context.getExternalFilesDir(null), "photos").apply {
        if (!exists()) mkdirs()
    }

    init {

        CoroutineScope(Dispatchers.IO).launch {
            cleanupOrphanedThumbnails()
        }
    }

    suspend fun savePhotoFromFile(sourceFile: File): PhotoReference {
        return withContext(Dispatchers.IO) {
            val photoId = UUID.randomUUID().toString()
            val optimizedFile = File(localStorageDir, "${photoId}.jpg")
            val thumbnailFile = File(localCacheDir, "${photoId}_thumb.jpg")
            val tempOptimizedFile = File(localStorageDir, "${photoId}_opt.tmp")
            val timestamp = System.currentTimeMillis()

            try {
                val stat = StatFs(localStorageDir.path)
                val availableBytes = stat.availableBytes
                val storageBuffer = 10 * 1024 * 1024 // 10MB buffer
                if (sourceFile.length() > availableBytes - storageBuffer) {
                    throw IOException("Not enough storage space. Required for source (approx): ${sourceFile.length()}, Available for use (after ${storageBuffer / (1024 * 1024)}MB buffer): ${availableBytes - storageBuffer}")
                }

                compressImage(
                    source = sourceFile,
                    dest = tempOptimizedFile,
                    quality = 85,
                    reqWidth = 2048,
                    reqHeight = 2048
                )

                if (!tempOptimizedFile.renameTo(optimizedFile)) {
                    tempOptimizedFile.delete()
                    throw IOException("Failed to save optimized photo: could not rename temp file.")
                }

                if (!optimizedFile.exists() || optimizedFile.length() == 0L) {
                    optimizedFile.delete()
                    throw IOException("Optimized file validation failed: File does not exist or is empty. Path: ${optimizedFile.absolutePath}")
                }

                createThumbnail(
                    source = sourceFile,
                    dest = thumbnailFile,
                    reqSize = 200,
                    quality = 80
                )

                if (!thumbnailFile.exists() || thumbnailFile.length() == 0L) {
                    thumbnailFile.delete()
                    throw IOException("Thumbnail file validation failed: File does not exist or is empty. Path: ${thumbnailFile.absolutePath}")
                }

                // ADD: Save to database after successful file operations
                val photoEntity = PhotoEntity(
                    id = photoId,
                    filePath = optimizedFile.absolutePath,
                    timestamp = timestamp
                )
                photoDao.insertPhoto(photoEntity)

                PhotoReference(
                    photoId,
                    optimizedFile.absolutePath,
                    thumbnailFile.absolutePath,
                    timestamp
                )

            } catch (e: Exception) {
                optimizedFile.delete()
                tempOptimizedFile.delete()
                thumbnailFile.delete()
                throw e
            }
        }
    }

    suspend fun savePhotoMetadata(
        photoId: String,
        latitude: Double?,
        longitude: Double?,
        address: String?,
        compassDirection: String,
        deviceOrientation: String
    ) {
        withContext(Dispatchers.IO) {
            val metadata = PhotoMetadataEntity(
                photoId = photoId,
                latitude = latitude,
                longitude = longitude,
                address = address,
                compassDirection = compassDirection,
                deviceOrientation = deviceOrientation,
                timestamp = System.currentTimeMillis()
            )
            photoDao.insertPhotoMetadata(metadata)
        }
    }

    suspend fun getPhotosWithMetadata(): List<PhotoWithMetadata> {
        return withContext(Dispatchers.IO) {
            photoDao.getPhotosWithMetadata()
        }
    }

    suspend fun getPhotoMetadata(photoId: String): PhotoMetadataEntity? {
        return withContext(Dispatchers.IO) {
            photoDao.getPhotoMetadata(photoId)
        }
    }

    private var photoCacheTimestamp = 0L
    private var cachedPhotos: List<PhotoReference>? = null
    private val CACHE_VALIDITY_MS = 5000L // 5 seconds

    suspend fun getAllPhotos(limit: Int = 100, offset: Int = 0): List<PhotoReference> {
        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (cachedPhotos != null && (now - photoCacheTimestamp) < CACHE_VALIDITY_MS) {
                return@withContext cachedPhotos!!
                    .drop(offset)
                    .take(limit)
            }

            // Get from database instead of filesystem for data consistency
            val photos = photoDao.getAllPhotos().mapNotNull { entity ->
                val optimizedFile = File(entity.filePath)
                val thumbnailFile = File(localCacheDir, "${entity.id}_thumb.jpg")

                // Verify files still exist (cleanup orphaned database records)
                if (optimizedFile.exists() && thumbnailFile.exists()) {
                    PhotoReference(
                        id = entity.id,
                        optimizedPath = entity.filePath,
                        thumbnailPath = thumbnailFile.absolutePath,
                        timestamp = entity.timestamp
                    )
                } else {
                    // Clean up orphaned database record
                    try {
                        photoDao.deletePhoto(entity.id)
                        photoDao.deletePhotoMetadata(entity.id)
                    } catch (e: Exception) {
                        Log.w(
                            "PhotoStorageManager",
                            "Failed to clean orphaned record: ${entity.id}"
                        )
                    }
                    null
                }
            }

            cachedPhotos = photos
            photoCacheTimestamp = now
            photos.drop(offset).take(limit)
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    @Throws(IOException::class)
    private fun compressImage(
        source: File,
        dest: File,
        quality: Int,
        reqWidth: Int = 2048,
        reqHeight: Int = 2048
    ) {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(source.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, options)
            ?: throw IOException("Failed to decode bitmap from ${source.absolutePath}")
        try {
            dest.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw IOException("Failed to compress bitmap to ${dest.absolutePath}")
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Throws(IOException::class)
    private fun createThumbnail(source: File, dest: File, reqSize: Int = 200, quality: Int = 80) {
        compressImage(source, dest, quality, reqSize, reqSize)
    }

    private fun findOrphanedThumbnails(): List<File> {
        val orphanedThumbnails = mutableListOf<File>()
        val allThumbnails =
            localCacheDir.listFiles { _, name -> name.endsWith("_thumb.jpg") } ?: emptyArray()

        for (thumbnailFile in allThumbnails) {
            val photoId = thumbnailFile.nameWithoutExtension.removeSuffix("_thumb")
            if (photoId.isNotEmpty()) {
                val correspondingOptimizedFile = File(localStorageDir, "${photoId}.jpg")
                if (!correspondingOptimizedFile.exists()) {
                    orphanedThumbnails.add(thumbnailFile)
                }
            }
        }
        return orphanedThumbnails
    }

    private fun cleanupOrphanedThumbnails() {
        val orphans = findOrphanedThumbnails()
        orphans.forEach { it.delete() }

        if (orphans.isNotEmpty()) {
            Log.d("PhotoStorageManager", "Cleaned ${orphans.size} orphaned thumbnails")
        }
    }

    suspend fun deletePhoto(photoId: String): Boolean = withContext(Dispatchers.IO) {
        if (photoId.isBlank()) {
            Log.w("PhotoStorageManager", "deletePhoto called with blank photoId.")
            return@withContext false
        }
        val optimizedFile = File(localStorageDir, "${photoId}.jpg")
        val thumbnailFile = File(localCacheDir, "${photoId}_thumb.jpg")

        var deletedOptimized = false
        if (optimizedFile.exists()) {
            deletedOptimized = optimizedFile.delete()
            if (!deletedOptimized) {
                Log.w(
                    "PhotoStorageManager",
                    "Failed to delete optimized file: ${optimizedFile.absolutePath}"
                )
            }
        } else {
            deletedOptimized = true
        }

        var deletedThumbnail = false
        if (thumbnailFile.exists()) {
            deletedThumbnail = thumbnailFile.delete()
            if (!deletedThumbnail) {
                Log.w(
                    "PhotoStorageManager",
                    "Failed to delete thumbnail file: ${thumbnailFile.absolutePath}"
                )
            }
        } else {
            deletedThumbnail = true
        }

        // Clean up database records
        if (deletedOptimized || deletedThumbnail) {
            try {
                // Delete photo and metadata records
                photoDao.deletePhoto(photoId)
                photoDao.deletePhotoMetadata(photoId)
            } catch (e: Exception) {
                Log.w(
                    "PhotoStorageManager",
                    "Failed to delete database records for photoId: $photoId",
                    e
                )
            }

            // Force complete cache invalidation
            cachedPhotos = null
            photoCacheTimestamp = 0L // Add this line to reset timestamp
        }

        return@withContext deletedOptimized && deletedThumbnail
    }
}