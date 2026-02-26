package com.example.weeklytotals

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.weeklytotals.data.AppDatabase
import com.example.weeklytotals.data.Transaction
import com.example.weeklytotals.data.TransactionDao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AdjustmentTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.transactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- Bug 1: Duplicate adjustments ---

    @Test
    fun `insertAdjustmentIfNotExists inserts when no adjustment exists`() = runTest {
        val adjustment = Transaction(
            weekStartDate = "2024-01-06",
            category = "ADJUSTMENT",
            amount = 50.0,
            isAdjustment = true,
            createdAt = 1000L
        )

        val id = dao.insertAdjustmentIfNotExists(adjustment)

        assertTrue("Should return a valid ID", id > 0)
        assertTrue(dao.hasAdjustmentForWeek("2024-01-06"))
    }

    @Test
    fun `insertAdjustmentIfNotExists prevents duplicate for same week`() = runTest {
        val first = Transaction(
            weekStartDate = "2024-01-06",
            category = "ADJUSTMENT",
            amount = 50.0,
            isAdjustment = true,
            createdAt = 1000L
        )
        val second = Transaction(
            weekStartDate = "2024-01-06",
            category = "ADJUSTMENT",
            amount = 75.0,
            isAdjustment = true,
            createdAt = 2000L
        )

        val firstId = dao.insertAdjustmentIfNotExists(first)
        val secondId = dao.insertAdjustmentIfNotExists(second)

        assertTrue("First insert should succeed", firstId > 0)
        assertEquals("Second insert should be rejected", -1L, secondId)

        // Verify only one adjustment exists
        val existing = dao.getAdjustmentForWeek("2024-01-06")
        assertNotNull(existing)
        assertEquals(50.0, existing!!.amount, 0.01)
    }

    @Test
    fun `insertAdjustmentIfNotExists allows adjustments for different weeks`() = runTest {
        val week1 = Transaction(
            weekStartDate = "2024-01-06",
            category = "ADJUSTMENT",
            amount = 50.0,
            isAdjustment = true,
            createdAt = 1000L
        )
        val week2 = Transaction(
            weekStartDate = "2024-01-13",
            category = "ADJUSTMENT",
            amount = 30.0,
            isAdjustment = true,
            createdAt = 2000L
        )

        val id1 = dao.insertAdjustmentIfNotExists(week1)
        val id2 = dao.insertAdjustmentIfNotExists(week2)

        assertTrue("Week 1 insert should succeed", id1 > 0)
        assertTrue("Week 2 insert should succeed", id2 > 0)
        assertNotNull(dao.getAdjustmentForWeek("2024-01-06"))
        assertNotNull(dao.getAdjustmentForWeek("2024-01-13"))
    }

    @Test
    fun `non-adjustment transactions do not block adjustment insertion`() = runTest {
        // Insert a regular transaction for the same week
        val regular = Transaction(
            weekStartDate = "2024-01-06",
            category = "GAS",
            amount = 40.0,
            isAdjustment = false,
            createdAt = 1000L
        )
        dao.insert(regular)

        // Adjustment should still be insertable
        val adjustment = Transaction(
            weekStartDate = "2024-01-06",
            category = "ADJUSTMENT",
            amount = 50.0,
            isAdjustment = true,
            createdAt = 2000L
        )
        val id = dao.insertAdjustmentIfNotExists(adjustment)
        assertTrue("Adjustment should insert despite regular transaction existing", id > 0)
    }

    // --- Bug 2: Adjustments should be updatable and deletable ---

    @Test
    fun `adjustment can be updated`() = runTest {
        val adjustment = Transaction(
            weekStartDate = "2024-01-06",
            category = "ADJUSTMENT",
            amount = 50.0,
            isAdjustment = true,
            createdAt = 1000L
        )
        val id = dao.insert(adjustment)

        // Update the amount
        val updated = adjustment.copy(id = id, amount = 75.0)
        dao.update(updated)

        val result = dao.getAdjustmentForWeek("2024-01-06")
        assertNotNull(result)
        assertEquals(75.0, result!!.amount, 0.01)
    }

    @Test
    fun `adjustment can be deleted`() = runTest {
        val adjustment = Transaction(
            weekStartDate = "2024-01-06",
            category = "ADJUSTMENT",
            amount = 50.0,
            isAdjustment = true,
            createdAt = 1000L
        )
        dao.insert(adjustment)

        val inserted = dao.getAdjustmentForWeek("2024-01-06")
        assertNotNull("Adjustment should exist after insert", inserted)

        dao.delete(inserted!!)

        val afterDelete = dao.getAdjustmentForWeek("2024-01-06")
        assertNull("Adjustment should be gone after delete", afterDelete)
        assertFalse(dao.hasAdjustmentForWeek("2024-01-06"))
    }

    // --- Sync deduplication scenario ---

    @Test
    fun `getAdjustmentForWeek returns existing adjustment regardless of createdAt`() = runTest {
        // Simulate: checkWeekRollover created an adjustment with createdAt=1000
        val local = Transaction(
            weekStartDate = "2024-01-06",
            category = "ADJUSTMENT",
            amount = 50.0,
            isAdjustment = true,
            createdAt = 1000L
        )
        dao.insert(local)

        // Firebase sync tries to find a local adjustment for this week
        // It should find the existing one even though it's looking for a different createdAt
        val existing = dao.getAdjustmentForWeek("2024-01-06")
        assertNotNull("Should find existing adjustment regardless of createdAt", existing)
        assertEquals(1000L, existing!!.createdAt)
    }

    @Test
    fun `updating adjustment createdAt aligns with remote`() = runTest {
        // Simulate: local adjustment with createdAt=1000
        val local = Transaction(
            weekStartDate = "2024-01-06",
            category = "ADJUSTMENT",
            amount = 50.0,
            isAdjustment = true,
            createdAt = 1000L
        )
        val id = dao.insert(local)

        // Firebase sync updates the local adjustment to match remote createdAt=2000
        val aligned = local.copy(id = id, createdAt = 2000L, amount = 60.0)
        dao.update(aligned)

        val result = dao.getAdjustmentForWeek("2024-01-06")
        assertNotNull(result)
        assertEquals(2000L, result!!.createdAt)
        assertEquals(60.0, result.amount, 0.01)
    }

    // --- Refund transactions ---

    @Test
    fun `refund transaction stores negative amount`() = runTest {
        val refund = Transaction(
            weekStartDate = "2024-01-06",
            category = "REFUND",
            amount = -25.0,
            createdAt = 3000L
        )
        dao.insert(refund)

        val cursor = db.openHelper.readableDatabase.query(
            "SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE weekStartDate = ?",
            arrayOf<Any>("2024-01-06")
        )
        cursor.moveToFirst()
        val total = cursor.getDouble(0)
        cursor.close()

        assertEquals(-25.0, total, 0.01)
    }

    @Test
    fun `refund reduces weekly total and increases remaining budget`() = runTest {
        // Add a regular expense
        dao.insert(Transaction(weekStartDate = "2024-01-06", category = "GAS", amount = 100.0, createdAt = 1000L))
        // Add a refund (stored as negative)
        dao.insert(Transaction(weekStartDate = "2024-01-06", category = "REFUND", amount = -30.0, createdAt = 2000L))

        val cursor = db.openHelper.readableDatabase.query(
            "SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE weekStartDate = ?",
            arrayOf<Any>("2024-01-06")
        )
        cursor.moveToFirst()
        val total = cursor.getDouble(0)
        cursor.close()

        // 100 - 30 = 70 spent, so remaining = budget - 70
        assertEquals(70.0, total, 0.01)
    }

    @Test
    fun `refund can push remaining beyond budget`() = runTest {
        // Only add a refund with no expenses
        dao.insert(Transaction(weekStartDate = "2024-01-06", category = "REFUND", amount = -50.0, createdAt = 1000L))

        val cursor = db.openHelper.readableDatabase.query(
            "SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE weekStartDate = ?",
            arrayOf<Any>("2024-01-06")
        )
        cursor.moveToFirst()
        val total = cursor.getDouble(0)
        cursor.close()

        // Total is -50, so remaining = budget - (-50) = budget + 50
        assertEquals(-50.0, total, 0.01)
    }

    // --- Transaction details ---

    @Test
    fun `transaction with details stores and retrieves correctly`() = runTest {
        val transaction = Transaction(
            weekStartDate = "2024-01-06",
            category = "GAS",
            amount = 40.0,
            createdAt = 4000L,
            details = "Shell station on Main St"
        )
        val id = dao.insert(transaction)

        val cursor = db.openHelper.readableDatabase.query(
            "SELECT details FROM transactions WHERE id = ?",
            arrayOf<Any>(id)
        )
        cursor.moveToFirst()
        val details = cursor.getString(0)
        cursor.close()

        assertEquals("Shell station on Main St", details)
    }

    @Test
    fun `transaction without details stores null`() = runTest {
        val transaction = Transaction(
            weekStartDate = "2024-01-06",
            category = "GAS",
            amount = 40.0,
            createdAt = 5000L
        )
        val id = dao.insert(transaction)

        val cursor = db.openHelper.readableDatabase.query(
            "SELECT details FROM transactions WHERE id = ?",
            arrayOf<Any>(id)
        )
        cursor.moveToFirst()
        assertTrue("Details should be null", cursor.isNull(0))
        cursor.close()
    }
}
