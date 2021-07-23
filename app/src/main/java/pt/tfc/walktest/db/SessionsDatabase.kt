package pt.tfc.walktest.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Session::class],
    version = 1
)
@TypeConverters(Converters::class)
abstract class SessionsDatabase : RoomDatabase() {

    abstract fun getRunDao(): SessionDAO
}