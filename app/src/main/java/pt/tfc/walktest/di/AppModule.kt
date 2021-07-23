package pt.tfc.walktest.di

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import pt.tfc.walktest.db.SessionsDatabase
import pt.tfc.walktest.other.Constants.KEY_FIRST_TIME_TOGGLE
import pt.tfc.walktest.other.Constants.RUNNING_DATABASE_NAME
import pt.tfc.walktest.other.Constants.SHARED_PREFERENCES_NAME
import javax.inject.Singleton

@Module
// Newer dependencies -> @InstallIn(SingletonComponent::class)
@InstallIn(ApplicationComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideRunningDatabase(
        @ApplicationContext app: Context
    ) = Room.databaseBuilder(
        app,
        SessionsDatabase::class.java,
        RUNNING_DATABASE_NAME
    ).build()

    @Singleton
    @Provides
    fun provideRunDao(db: SessionsDatabase) = db.getRunDao()

    @Singleton
    @Provides
    fun provideSharedPreferences(@ApplicationContext app: Context) =
        app.getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE)

    @Singleton
    @Provides
    fun provideFirstTimeToogle(sharedPref: SharedPreferences) = sharedPref.getBoolean(
        KEY_FIRST_TIME_TOGGLE, true)

}