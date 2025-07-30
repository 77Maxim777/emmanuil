import android.app.Application

class Application : Application() {
    companion object {
        lateinit var instance: Application
        private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
