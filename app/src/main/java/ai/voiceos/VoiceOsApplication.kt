package ai.voiceos

import android.app.Application
import androidx.room.Room
import ai.voiceos.data.HistoryDatabase
import ai.voiceos.memory.NicknameStore
import ai.voiceos.settings.SettingsStore

class VoiceOsApplication : Application() {
    internal val settings by lazy { SettingsStore(this) }
    internal val memory by lazy { NicknameStore(this) }
    private val database by lazy {
        Room.databaseBuilder(this, HistoryDatabase::class.java, "voice_os_history.db").build()
    }
    internal val history get() = database.history()
}
