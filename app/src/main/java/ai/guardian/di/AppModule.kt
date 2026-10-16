package ai.guardian.di

import ai.guardian.data.db.GuardianDatabase
import ai.guardian.data.db.ScanDao
import ai.guardian.engine.Analyzer
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Wall clock, injectable so tests can control time. */
open class AppClock @Inject constructor() {
    open fun now(): Long = System.currentTimeMillis()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GuardianDatabase =
        Room.databaseBuilder(context, GuardianDatabase::class.java, GuardianDatabase.NAME).build()

    @Provides
    fun provideScanDao(db: GuardianDatabase): ScanDao = db.scanDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("user_settings") }

    /** v0.1: rules only + NoopIntelProvider. Swap detectors/intel here for v0.2. */
    @Provides
    @Singleton
    fun provideAnalyzer(clock: AppClock): Analyzer = Analyzer(clock = clock::now)
}
