package com.capytutor.data.db

import android.content.Context
import androidx.room.AutoMigration
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
        QuizEntity::class,
        QuizAttemptEntity::class,
        BowAwardEntity::class,
    ],
    // Version 2 (E11/E12) only adds tables; the auto-migration keeps everything already saved.
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class CapyDatabase : RoomDatabase() {
    abstract fun lessons(): LessonDao
    abstract fun sessions(): SessionDao
    abstract fun usage(): UsageDao
    abstract fun quizzes(): QuizDao
    abstract fun bows(): BowDao

    companion object {
        const val NAME = "capy_tutor.db"

        fun open(context: Context): CapyDatabase =
            Room.databaseBuilder(context.applicationContext, CapyDatabase::class.java, NAME).build()
    }
}
