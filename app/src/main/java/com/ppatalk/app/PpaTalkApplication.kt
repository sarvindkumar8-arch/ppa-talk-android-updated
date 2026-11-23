package com.ppatalk.app

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

class PpaTalkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        val appCheck = FirebaseAppCheck.getInstance()
        AppCheckProviderInstaller.install(appCheck)

        appCheck.getAppCheckToken(false)
            .addOnSuccessListener { result ->
                Log.i(
                    "PPA_APP_CHECK",
                    "TOKEN_SUCCESS; expiresAt=${result.expireTimeMillis}"
                )
            }
            .addOnFailureListener { error ->
                Log.e(
                    "PPA_APP_CHECK",
                    "TOKEN_FAILED; ${error.javaClass.simpleName}: ${error.message}"
                )
            }
    }
}
