package ai.guardian

import ai.guardian.platform.work.RetentionWorker
import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GuardianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RetentionWorker.schedule(this)
    }
}
