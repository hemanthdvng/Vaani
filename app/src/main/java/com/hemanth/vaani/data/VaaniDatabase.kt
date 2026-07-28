package com.hemanth.vaani.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class ScreeningOutcomeConverters {
    @TypeConverter
    fun fromOutcome(value: ScreeningOutcome): String = value.name

    @TypeConverter
    fun toOutcome(value: String): ScreeningOutcome = ScreeningOutcome.valueOf(value)
}

@Database(
    entities = [CallLogEntity::class, SpamNumberEntity::class, WhitelistEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(ScreeningOutcomeConverters::class)
abstract class VaaniDatabase : RoomDatabase() {

    abstract fun vaaniDao(): VaaniDao

    companion object {
        @Volatile private var instance: VaaniDatabase? = null

        fun getInstance(context: Context): VaaniDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VaaniDatabase::class.java,
                    "vaani.db"
                ).build().also { instance = it }
            }
    }
}
