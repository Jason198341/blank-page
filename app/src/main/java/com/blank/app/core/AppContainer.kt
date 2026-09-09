package com.blank.app.core

import android.content.Context
import com.blank.app.data.local.AppDatabase
import com.blank.app.data.prefs.SettingsStore

/** 의존성 한 뭉치. 개인용 앱 하나에 DI 프레임워크까지 끌어올 이유가 없다. */
class AppContainer private constructor(context: Context) {

    private val app = context.applicationContext
    val db: AppDatabase = AppDatabase.get(app)
    val settings = SettingsStore(app)
    val repository = BlankRepository(app, db, settings)

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun get(context: Context): AppContainer = instance ?: synchronized(this) {
            instance ?: AppContainer(context).also { instance = it }
        }
    }
}
