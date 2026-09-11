package com.yc.iqoolike

import android.app.Application
import com.yc.iqoolike.data.TokenRepository

class IQOOApplication : Application() {

    lateinit var repository: TokenRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = TokenRepository.getInstance(this)
    }

    companion object {
        lateinit var instance: IQOOApplication
            private set
    }
}
