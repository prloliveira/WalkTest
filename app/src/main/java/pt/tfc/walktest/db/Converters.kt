package pt.tfc.walktest.db

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.TypeConverter
import java.io.ByteArrayOutputStream
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {

    @TypeConverter
    fun toBitmap(bytes: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    @TypeConverter
    fun fromBitmap(bmp: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }

    @TypeConverter
    fun fromString(value: String?): ArrayList<String> {
        val listType =object :TypeToken<ArrayList<String>>(){}.type
        return Gson().fromJson(value, listType)
    }
    @TypeConverter
    fun fromArrayList(list: ArrayList<String?>): String {
        return Gson().toJson(list)
    }
}