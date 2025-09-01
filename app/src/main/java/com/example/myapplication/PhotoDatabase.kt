package com.example.myapplication

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

//Defining Room database with two tables - Photoentity and PhotoMetadataEntity
@Database(
    entities = [PhotoEntity::class, PhotoMetadataEntity::class],
    version = 1,
    exportSchema = false
)

//Data Access Object for interacting with the database
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

//Ensuring that there is only instance of the database in memory
    companion object {
        @Volatile
        private var INSTANCE: PhotoDatabase? = null

        fun getDatabase(context: Context): PhotoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotoDatabase::class.java,
                    "photo_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}