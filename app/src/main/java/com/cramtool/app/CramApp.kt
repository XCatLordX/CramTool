package com.cramtool.app

import android.app.Application
import com.cramtool.app.data.AppDatabase
import com.cramtool.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class CramApp : Application() {
    lateinit var repository: Repository
        private set

    /** 不隨畫面結束而取消的 scope，用來確保作答紀錄寫入 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, AppDatabase.create(this))
    }
}
