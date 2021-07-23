package pt.tfc.walktest.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SessionDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)

    @Delete
    suspend fun deleteSession(session: Session)

    @Query("SELECT * from session_table WHERE captureSession = :key")
    fun getSession(key: Long): Session

    @Query("SELECT * FROM session_table ORDER BY timestamp DESC")
    fun getAllSessionsSortedByDate(): LiveData<List<Session>>

    @Query("SELECT * FROM session_table ORDER BY timeInMillis DESC")
    fun getAllSessionsSortedByTimeInMillis(): LiveData<List<Session>>

    @Query("SELECT * FROM session_table ORDER BY distanceInMeters DESC")
    fun getAllSessionsSortedByDistance(): LiveData<List<Session>>

}