package com.sonicvault.app.data.nonce

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for SonicVault.
 *
 * Contains nonce_accounts table for the durable nonce pool (SonicSafe).
 */
@Database(entities = [NonceAccountEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noncePoolDao(): NoncePoolDao

    companion object {
        private const val DB_NAME = "sonicvault.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).build().also { instance = it }
            }
        }
    }
}
