package com.whxbill.android

import android.app.Application
import com.whxbill.android.data.ApiClient
import com.whxbill.android.data.BillRepository
import com.whxbill.android.data.SessionStore

class WhxBillApplication : Application() {
    lateinit var sessionStore: SessionStore
        private set
    lateinit var repository: BillRepository
        private set

    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        repository = BillRepository(ApiClient.service, sessionStore)
    }
}
