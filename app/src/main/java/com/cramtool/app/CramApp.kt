package com.cramtool.app

import android.app.Application
import com.cramtool.app.audio.SoundEffects
import com.cramtool.app.data.AppDatabase
import com.cramtool.app.data.AppSettings
import com.cramtool.app.data.NoteRepository
import com.cramtool.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class CramApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var notes: NoteRepository
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var sounds: SoundEffects
        private set

    /** 不隨畫面結束而取消的 scope，用來確保作答紀錄寫入 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        sounds = SoundEffects(this, settings)
        val db = AppDatabase.create(this)
        repository = Repository(this, db)
        notes = NoteRepository(this, db)
    }
}
