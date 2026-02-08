package com.example.weeklytotals

import com.example.weeklytotals.sms.SmsTransactionDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsTransactionDetectorTest {

    @Test
    fun detectsTdPurchaseAlert() {
        val msg = "TD Alert: Purchase of \$45.67 CAD at SHOPPERS DRUG MART"
        val result = SmsTransactionDetector.detect(msg)
        assertNotNull(result)
        assertEquals(45.67, result!!.amount, 0.001)
    }

    @Test
    fun detectsRbcTransactionNotification() {
        val msg = "RBC: Your Visa card ending in 1234 was charged \$123.45 at AMAZON.CA"
        val result = SmsTransactionDetector.detect(msg)
        assertNotNull(result)
        assertEquals(123.45, result!!.amount, 0.001)
    }

    @Test
    fun detectsCadAmountSuffix() {
        val msg = "Alert: Transaction of 89.99 CAD on your card at WALMART"
        val result = SmsTransactionDetector.detect(msg)
        assertNotNull(result)
        assertEquals(89.99, result!!.amount, 0.001)
    }

    @Test
    fun detectsDollarSignAfterAmount() {
        val msg = "Alerte: Achat de 52.30\$ chez METRO"
        val result = SmsTransactionDetector.detect(msg)
        assertNotNull(result)
        assertEquals(52.30, result!!.amount, 0.001)
    }

    @Test
    fun detectsLargeAmountWithComma() {
        val msg = "TD Alert: Purchase of \$1,234.56 CAD at BEST BUY"
        val result = SmsTransactionDetector.detect(msg)
        assertNotNull(result)
        assertEquals(1234.56, result!!.amount, 0.001)
    }

    @Test
    fun detectsInteracPayment() {
        val msg = "CIBC: Interac purchase of \$15.00 at TIM HORTONS"
        val result = SmsTransactionDetector.detect(msg)
        assertNotNull(result)
        assertEquals(15.00, result!!.amount, 0.001)
    }

    @Test
    fun ignoresDepositMessage() {
        val msg = "TD: Direct deposit of \$2,500.00 received"
        val result = SmsTransactionDetector.detect(msg)
        assertNull(result)
    }

    @Test
    fun ignoresRefundMessage() {
        val msg = "RBC: Refund of \$45.00 has been credited to your account"
        val result = SmsTransactionDetector.detect(msg)
        assertNull(result)
    }

    @Test
    fun ignoresETransferReceived() {
        val msg = "BMO: e-Transfer received of \$100.00 from John"
        val result = SmsTransactionDetector.detect(msg)
        assertNull(result)
    }

    @Test
    fun ignoresNonBankMessage() {
        val msg = "Hey, want to grab lunch tomorrow?"
        val result = SmsTransactionDetector.detect(msg)
        assertNull(result)
    }

    @Test
    fun detectsWithdrawalMessage() {
        val msg = "Scotiabank: ATM withdrawal of \$200.00 at Main St branch"
        val result = SmsTransactionDetector.detect(msg)
        assertNotNull(result)
        assertEquals(200.00, result!!.amount, 0.001)
    }

    @Test
    fun detectsFrenchFormatCommaDecimal() {
        val msg = "Alerte: Achat de 45,67\$ chez PHARMAPRIX"
        val result = SmsTransactionDetector.detect(msg)
        assertNotNull(result)
        assertEquals(45.67, result!!.amount, 0.001)
    }

    @Test
    fun detectsPaymentKeyword() {
        val msg = "BMO Alert: Payment of \$75.50 processed"
        val result = SmsTransactionDetector.detect(msg)
        assertNotNull(result)
        assertEquals(75.50, result!!.amount, 0.001)
    }

    @Test
    fun detectsCadPrefix() {
        val msg = "Alert: Your card was used for CAD 99.99 at COSTCO"
        val result = SmsTransactionDetector.detect(msg)
        assertNotNull(result)
        assertEquals(99.99, result!!.amount, 0.001)
    }

    @Test
    fun rawMessagePreservedInResult() {
        val msg = "TD Alert: Purchase of \$10.00 CAD at COFFEE SHOP"
        val result = SmsTransactionDetector.detect(msg)
        assertNotNull(result)
        assertEquals(msg, result!!.rawMessage)
    }
}
