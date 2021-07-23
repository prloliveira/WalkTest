package pt.tfc.walktest.db

import android.graphics.Bitmap
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_table")
data class Session(
    var img: Bitmap? = null,
    var timestamp: Long = 0L,
    var distanceInMeters: Int = 0,
    var timeInMillis: Long = 0L,
    var captureSession: ArrayList<String>
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}