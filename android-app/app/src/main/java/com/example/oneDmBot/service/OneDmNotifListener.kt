package com.example.oneDmBot.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.oneDmBot.db.Settings

class OneDmNotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        val expected = Settings(this).oneDmPackage
        val knownPkgs = setOf(
            expected,
            "idm.internet.download.manager",
            "idm.internet.download.manager.plus",
            "com.dv.adm",
            "com.dv.adm.pay"
        )
        if (pkg !in knownPkgs) return
        MovieAutoDownloadService.instance?.notifyDownloadStarted()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* no-op */ }
}
