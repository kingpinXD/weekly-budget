package com.example.weeklytotals.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.weeklytotals.TransactionPromptActivity
import com.example.weeklytotals.data.BudgetPreferences

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = BudgetPreferences(context)
        if (!prefs.isAutoTransactionsEnabled()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        // Concatenate all message parts into one body
        val fullBody = messages.joinToString("") { it.messageBody ?: "" }

        if (fullBody.isBlank()) return

        val detected = SmsTransactionDetector.detect(fullBody) ?: return

        val promptIntent = Intent(context, TransactionPromptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TransactionPromptActivity.EXTRA_AMOUNT, detected.amount)
            putExtra(TransactionPromptActivity.EXTRA_RAW_MESSAGE, detected.rawMessage)
        }
        context.startActivity(promptIntent)
    }
}
