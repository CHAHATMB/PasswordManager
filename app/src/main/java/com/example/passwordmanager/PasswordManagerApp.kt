package com.example.passwordmanager

import android.app.Application

class PasswordManagerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        container = AppContainer(this)
    }
}
