package com.blank.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun toItemKind(v: String) = ItemKind.valueOf(v)
    @TypeConverter fun fromItemKind(v: ItemKind) = v.name
    @TypeConverter fun toRoundState(v: String) = RoundState.valueOf(v)
    @TypeConverter fun fromRoundState(v: RoundState) = v.name
    @TypeConverter fun toGrade(v: String) = Grade.valueOf(v)
    @TypeConverter fun fromGrade(v: Grade) = v.name
    @TypeConverter fun toSubmissionKind(v: String) = SubmissionKind.valueOf(v)
    @TypeConverter fun fromSubmissionKind(v: SubmissionKind) = v.name
    @TypeConverter fun toEngine(v: String) = Engine.valueOf(v)
    @TypeConverter fun fromEngine(v: Engine) = v.name
}

@Database(
    entities = [
        ItemEntity::class, RevisionEntity::class, RoundEntity::class,
        SubmissionEntity::class, ComparisonEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun revisions(): RevisionDao
    abstract fun rounds(): RoundDao
    abstract fun submissions(): SubmissionDao
    abstract fun comparisons(): ComparisonDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "blank.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
