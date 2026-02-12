package com.example.weeklytotals.notification

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.weeklytotals.TransactionPromptActivity
import com.example.weeklytotals.data.BudgetPreferences
import com.example.weeklytotals.sms.SmsTransactionDetector

class TransactionNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = BudgetPreferences(this)
        if (!prefs.isAutoTransactionsEnabled()) return

        val monitoredApps = prefs.getMonitoredApps()
        if (sbn.packageName !in monitoredApps) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        val body = bigText ?: text
        val fullMessage = if (title.isNotBlank() && body.isNotBlank()) {
            "$title: $body"
        } else {
            title + body
        }

        if (fullMessage.isBlank()) return

        val detected = SmsTransactionDetector.detect(fullMessage) ?: return

        val promptIntent = Intent(this, TransactionPromptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TransactionPromptActivity.EXTRA_AMOUNT, detected.amount)
            putExtra(TransactionPromptActivity.EXTRA_RAW_MESSAGE, detected.rawMessage)
        }
        startActivity(promptIntent)
    }
}
