package com.example.weeklytotals.sms

data class DetectedTransaction(
    val amount: Double,
    val rawMessage: String
)

object SmsTransactionDetector {

    // Words that indicate incoming money (should be excluded)
    private val creditKeywords = listOf(
        "deposit", "refund", "credit", "received", "e-transfer received",
        "direct deposit", "reimbursement", "cashback"
    )

    // Patterns that match dollar amounts in various formats
    private val amountPatterns = listOf(
        // $123.45 or $1,234.56
        Regex("""\$\s?([\d,]+\.\d{2})"""),
        // 123.45$ or 1,234.56$ (Quebec-style)
        Regex("""([\d,]+\.\d{2})\s?\$"""),
        // CAD 123.45 or CAD 1,234.56
        Regex("""CAD\s?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE),
        // 123.45 CAD or 1,234.56 CAD
        Regex("""([\d,]+\.\d{2})\s?CAD""", RegexOption.IGNORE_CASE),
        // 123,45$ (French format with comma as decimal)
        Regex("""([\d ]+,\d{2})\s?\$"""),
        // Amount: 123.45 or Montant: 123.45
        Regex("""(?:amount|montant)\s*:?\s*\$?\s?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    )

    // Transaction indicator keywords (debit/charge patterns)
    private val transactionKeywords = listOf(
        "purchase", "achat", "transaction", "charged", "spent",
        "withdrawal", "retrait", "payment", "paiement", "debit",
        "preauthorized", "pos ", "interac", "tap ", "contactless",
        "alert", "alerte", "notification"
    )

    fun detect(messageBody: String): DetectedTransaction? {
        val lowerMessage = messageBody.lowercase()

        // Skip if the message looks like a credit/deposit
        if (creditKeywords.any { lowerMessage.contains(it) }) {
            return null
        }

        // Check if message has transaction-related keywords
        val hasTransactionKeyword = transactionKeywords.any { lowerMessage.contains(it) }

        // Try each amount pattern
        for (pattern in amountPatterns) {
            val match = pattern.find(messageBody)
            if (match != null) {
                val rawAmount = match.groupValues[1]
                val amount = parseAmount(rawAmount)
                if (amount != null && amount > 0) {
                    // If we found an amount and the message has a transaction keyword, it's a match.
                    // Also match if the message is short (typical SMS alert) and has a dollar amount.
                    if (hasTransactionKeyword || messageBody.length < 200) {
                        return DetectedTransaction(amount, messageBody)
                    }
                }
            }
        }

        return null
    }

    private fun parseAmount(raw: String): Double? {
        // Handle French format: "1 234,56" -> "1234.56"
        if (raw.contains(',') && !raw.contains('.')) {
            val normalized = raw.replace(" ", "").replace(",", ".")
            return normalized.toDoubleOrNull()
        }
        // Handle standard format: "1,234.56" -> "1234.56"
        val normalized = raw.replace(",", "")
        return normalized.toDoubleOrNull()
    }
}
