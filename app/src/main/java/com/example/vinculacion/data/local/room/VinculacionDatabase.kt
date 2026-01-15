package com.example.vinculacion.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.vinculacion.data.local.room.dao.AvesDao
import com.example.vinculacion.data.local.room.dao.CategoriasDao
import com.example.vinculacion.data.local.room.dao.MediaRecordsDao
import com.example.vinculacion.data.local.room.dao.SyncTasksDao
import com.example.vinculacion.data.local.room.dao.TourParticipantsDao
import com.example.vinculacion.data.local.room.dao.ToursDao
import com.example.vinculacion.data.local.room.dao.UserAccountsDao
import com.example.vinculacion.data.local.room.entities.AveEntity
import com.example.vinculacion.data.local.room.entities.CategoriaEntity
import com.example.vinculacion.data.local.room.entities.MediaRecordEntity
import com.example.vinculacion.data.local.room.entities.SyncTaskEntity
import com.example.vinculacion.data.local.room.entities.TourEntity
import com.example.vinculacion.data.local.room.entities.TourParticipantEntity
import com.example.vinculacion.data.local.room.entities.UserAccountEntity

@Database(
    entities = [
        AveEntity::class,
        CategoriaEntity::class,
        MediaRecordEntity::class,
        TourEntity::class,
        TourParticipantEntity::class,
        SyncTaskEntity::class,
        UserAccountEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(RoomTypeConverters::class)
abstract class VinculacionDatabase : RoomDatabase() {

    abstract fun avesDao(): AvesDao
    abstract fun categoriasDao(): CategoriasDao
    abstract fun mediaRecordsDao(): MediaRecordsDao
    abstract fun toursDao(): ToursDao
    abstract fun tourParticipantsDao(): TourParticipantsDao
    abstract fun syncTasksDao(): SyncTasksDao
    abstract fun userAccountsDao(): UserAccountsDao

    companion object {
        private const val DATABASE_NAME = "vinculacion.db"

        @Volatile
        private var instance: VinculacionDatabase? = null

        fun getInstance(context: Context): VinculacionDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): VinculacionDatabase =
            Room.databaseBuilder(context, VinculacionDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration() // Reemplazar por migraciones reales antes de salir a producción
                .build()
    }
}
