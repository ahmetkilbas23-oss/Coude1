package com.example.oneDmBot.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.oneDmBot.db.Settings

class OneDmNotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        val expected = Settings(this).oneDmPackage
        if (pkg != expected && pkg != "com.dv.adm" && pkg != "com.dv.adm.pay") return
        MovieAutoDownloadService.instance?.notifyDownloadStarted()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* no-op */ }
}
