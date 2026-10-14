package com.capytutor.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LessonEntity::class,
        KeyIdeaEntity::class,
        SessionEntity::class,
        TurnEntity::class,
        IdeaResultEntity::class,
        ProviderUsageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CapyDatabase : RoomDatabase() {
    abstract fun lessons(): LessonDao
    abstract fun sessions(): SessionDao
    abstract fun usage(): UsageDao

    companion object {
        const val NAME = "capy_tutor.db"

        fun open(context: Context): CapyDatabase =
            Room.databaseBuilder(context.applicationContext, CapyDatabase::class.java, NAME).build()
    }
}
