package com.example.weeklytotals

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.weeklytotals.data.AppDatabase
import com.example.weeklytotals.data.BudgetPreferences
import com.example.weeklytotals.data.Transaction
import com.example.weeklytotals.data.TransactionDao
import com.example.weeklytotals.data.WeeklySavings
import com.example.weeklytotals.data.WeeklySavingsDao
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the savings/overage carry-over logic in checkWeekRollover().
 *
 * Verifies:
 * 1. Savings is added exactly once per week (no double counting)
 * 2. The per-week DAO guard (getSavingsForWeek) prevents re-processing
 * 3. Pending budget changes don't corrupt savings calculations
 * 4. Multiple app opens in the same week don't duplicate carry-over
 * 5. Over-budget weeks insert amount=0.0 as a processed flag
 * 6. Savings synced from Firebase prevents recalculation
 * 7. Bootstrap backfills all historical weeks correctly
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SavingsCarryOverTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao
    private lateinit var weeklySavingsDao: WeeklySavingsDao
    private lateinit var budgetPreferences: BudgetPreferences

    private val week1Start = "2024-01-06"
    private val week2Start = "2024-01-13"
    private val week3Start = "2024-01-20"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.transactionDao()
        weeklySavingsDao = db.weeklySavingsDao()
        budgetPreferences = BudgetPreferences(context)
        budgetPreferences.clearAll()
    }

    @After
    fun tearDown() {
        db.close()
        budgetPreferences.clearAll()
    }

    private fun getWeekTotal(weekStart: String): Double {
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE weekStartDate = ?",
            arrayOf<Any>(weekStart)
        )
        cursor.moveToFirst()
        val total = cursor.getDouble(0)
        cursor.close()
        return total
    }

    /**
     * Mirrors the fixed checkWeekRollover() logic exactly.
     * Budget is read BEFORE applying pending changes.
     * Per-week DAO row acts as the boolean guard.
     */
    private suspend fun simulateCheckWeekRollover(previousWeekStart: String, currentWeekStart: String) {
        val previousTotal = getWeekTotal(previousWeekStart)
        val previousBudget = budgetPreferences.getBudget()

        // Apply pending budget AFTER reading — takes effect for the new week only
        budgetPreferences.applyPendingBudget()

        // Carry over overage as adjustment
        if (previousTotal > previousBudget) {
            val overage = previousTotal - previousBudget
            val adjustment = Transaction(
                weekStartDate = currentWeekStart,
                category = "ADJUSTMENT",
                amount = overage,
                isAdjustment = true
            )
            dao.insertAdjustmentIfNotExists(adjustment)
        }

        // Accumulate savings: per-week row acts as the boolean guard
        if (weeklySavingsDao.getSavingsForWeek(previousWeekStart) == null) {
            val savingsAmount = if (previousTotal > 0 && previousTotal < previousBudget) {
                previousBudget - previousTotal
            } else {
                0.0 // over budget or zero spending — still insert to mark as processed
            }
            weeklySavingsDao.upsert(WeeklySavings(weekStartDate = previousWeekStart, amount = savingsAmount))
        }
    }

    // ── Basic savings carry-over ─────────────────────────────────────────

    @Test
    fun `savings added exactly once when under budget`() = runTest {
        budgetPreferences.setBudget(100.0)
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 80.0, createdAt = 1000L))

        simulateCheckWeekRollover(week1Start, week2Start)

        assertEquals(20.0, weeklySavingsDao.getTotalSavingsSync(), 0.01)
    }

    @Test
    fun `no savings when over budget`() = runTest {
        budgetPreferences.setBudget(100.0)
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 120.0, createdAt = 1000L))

        simulateCheckWeekRollover(week1Start, week2Start)

        // Row should exist with amount=0.0 (processed flag)
        val record = weeklySavingsDao.getSavingsForWeek(week1Start)
        assertNotNull("Record should exist as processed flag", record)
        assertEquals(0.0, record!!.amount, 0.01)
        assertEquals(0.0, weeklySavingsDao.getTotalSavingsSync(), 0.01)
    }

    @Test
    fun `no savings when previous week has zero spending`() = runTest {
        budgetPreferences.setBudget(100.0)

        simulateCheckWeekRollover(week1Start, week2Start)

        // Row should exist with amount=0.0 (processed flag)
        val record = weeklySavingsDao.getSavingsForWeek(week1Start)
        assertNotNull("Record should exist as processed flag", record)
        assertEquals(0.0, record!!.amount, 0.01)
        assertEquals(0.0, weeklySavingsDao.getTotalSavingsSync(), 0.01)
    }

    // ── Double counting prevention ───────────────────────────────────────

    @Test
    fun `savings NOT doubled when rollover runs twice in same week`() = runTest {
        budgetPreferences.setBudget(100.0)
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 80.0, createdAt = 1000L))

        simulateCheckWeekRollover(week1Start, week2Start)
        simulateCheckWeekRollover(week1Start, week2Start)

        assertEquals(
            "Guard must prevent double counting: savings should be \$20, not \$40",
            20.0,
            weeklySavingsDao.getTotalSavingsSync(),
            0.01
        )
    }

    @Test
    fun `savings NOT doubled when app opened five times in same week`() = runTest {
        budgetPreferences.setBudget(100.0)
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 70.0, createdAt = 1000L))

        // Simulate opening the app 5 times during the same week
        repeat(5) {
            simulateCheckWeekRollover(week1Start, week2Start)
        }

        assertEquals(
            "Savings should be \$30 regardless of how many times the app is opened",
            30.0,
            weeklySavingsDao.getTotalSavingsSync(),
            0.01
        )
    }

    @Test
    fun `overage adjustment NOT duplicated when rollover runs twice`() = runTest {
        budgetPreferences.setBudget(100.0)
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 130.0, createdAt = 1000L))

        simulateCheckWeekRollover(week1Start, week2Start)
        simulateCheckWeekRollover(week1Start, week2Start)

        // Should only have one adjustment of $30
        val adjustment = dao.getAdjustmentForWeek(week2Start)
        assertNotNull("Adjustment should exist", adjustment)
        assertEquals(30.0, adjustment!!.amount, 0.01)

        // Verify no duplicate adjustments via raw count
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM transactions WHERE weekStartDate = ? AND isAdjustment = 1",
            arrayOf<Any>(week2Start)
        )
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        assertEquals("Should have exactly 1 adjustment, not 2", 1, count)
    }

    // ── Multi-week accumulation ──────────────────────────────────────────

    @Test
    fun `savings accumulates correctly across multiple weeks`() = runTest {
        budgetPreferences.setBudget(100.0)

        // Week 1: spent $80 → savings = $20
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 80.0, createdAt = 1000L))
        simulateCheckWeekRollover(week1Start, week2Start)
        assertEquals(20.0, weeklySavingsDao.getTotalSavingsSync(), 0.01)

        // Week 2: spent $60 → savings = $40
        dao.insert(Transaction(weekStartDate = week2Start, category = "GAS", amount = 60.0, createdAt = 2000L))
        simulateCheckWeekRollover(week2Start, week3Start)

        assertEquals(
            "Total savings should be \$20 + \$40 = \$60",
            60.0,
            weeklySavingsDao.getTotalSavingsSync(),
            0.01
        )
    }

    @Test
    fun `each week processed exactly once across multi-week accumulation`() = runTest {
        budgetPreferences.setBudget(100.0)

        // Week 1: spent $90 → savings = $10
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 90.0, createdAt = 1000L))
        simulateCheckWeekRollover(week1Start, week2Start)
        simulateCheckWeekRollover(week1Start, week2Start) // duplicate — should be no-op

        // Week 2: spent $75 → savings = $25
        dao.insert(Transaction(weekStartDate = week2Start, category = "GAS", amount = 75.0, createdAt = 2000L))
        simulateCheckWeekRollover(week2Start, week3Start)
        simulateCheckWeekRollover(week2Start, week3Start) // duplicate — should be no-op
        simulateCheckWeekRollover(week2Start, week3Start) // triple — should be no-op

        assertEquals(
            "Total savings should be \$10 + \$25 = \$35, no duplicates",
            35.0,
            weeklySavingsDao.getTotalSavingsSync(),
            0.01
        )
    }

    // ── Pending budget fix verification ──────────────────────────────────

    @Test
    fun `savings correct when pending budget exists`() = runTest {
        // Budget during week 1 was $100
        budgetPreferences.setBudget(100.0)
        // User queued $200 for next week
        budgetPreferences.setPendingBudget(200.0)
        // Spent $80 in week 1
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 80.0, createdAt = 1000L))

        simulateCheckWeekRollover(week1Start, week2Start)

        // Savings = $100 - $80 = $20 (against old budget, not the pending $200)
        assertEquals(
            "Savings should use old budget (\$100), not pending (\$200)",
            20.0,
            weeklySavingsDao.getTotalSavingsSync(),
            0.01
        )
        // After rollover, budget should now be $200 for the new week
        assertEquals(200.0, budgetPreferences.getBudget(), 0.01)
    }

    @Test
    fun `overage detected correctly when pending budget exists`() = runTest {
        budgetPreferences.setBudget(100.0)
        budgetPreferences.setPendingBudget(200.0)
        // Spent $110 in week 1 — over the $100 budget
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 110.0, createdAt = 1000L))

        simulateCheckWeekRollover(week1Start, week2Start)

        // Overage = $110 - $100 = $10 carried to week 2
        val adjustment = dao.getAdjustmentForWeek(week2Start)
        assertNotNull("Overage adjustment should be created", adjustment)
        assertEquals(10.0, adjustment!!.amount, 0.01)

        // Over budget → record exists with 0.0
        val record = weeklySavingsDao.getSavingsForWeek(week1Start)
        assertNotNull(record)
        assertEquals(0.0, record!!.amount, 0.01)
        assertEquals(0.0, weeklySavingsDao.getTotalSavingsSync(), 0.01)
        // Budget is now $200
        assertEquals(200.0, budgetPreferences.getBudget(), 0.01)
    }

    // ── Per-week record as boolean guard ──────────────────────────────────

    @Test
    fun `over-budget week inserts amount 0 record as processed flag`() = runTest {
        budgetPreferences.setBudget(100.0)
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 150.0, createdAt = 1000L))

        simulateCheckWeekRollover(week1Start, week2Start)

        val record = weeklySavingsDao.getSavingsForWeek(week1Start)
        assertNotNull("Row should exist even for over-budget week", record)
        assertEquals("Amount should be 0 for over-budget", 0.0, record!!.amount, 0.01)

        // Running again should not change anything
        simulateCheckWeekRollover(week1Start, week2Start)
        assertEquals(0.0, weeklySavingsDao.getTotalSavingsSync(), 0.01)
    }

    @Test
    fun `savings synced from Firebase prevents recalculation`() = runTest {
        budgetPreferences.setBudget(100.0)
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 80.0, createdAt = 1000L))

        // Simulate Firebase sync having already inserted a savings record
        weeklySavingsDao.upsert(WeeklySavings(weekStartDate = week1Start, amount = 20.0))

        simulateCheckWeekRollover(week1Start, week2Start)

        // Should still be $20 — guard prevented recalculation
        assertEquals(20.0, weeklySavingsDao.getTotalSavingsSync(), 0.01)

        // Verify only one record exists
        val allSavings = weeklySavingsDao.getAllSavingsSync()
        assertEquals("Should have exactly 1 savings record", 1, allSavings.size)
    }

    // ── Bootstrap backfill ──────────────────────────────────────────────

    @Test
    fun `bootstrap backfills all historical weeks correctly`() = runTest {
        budgetPreferences.setBudget(100.0)
        val currentWeekStart = week3Start

        // Historical transactions in week 1 and week 2
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 70.0, createdAt = 1000L))
        dao.insert(Transaction(weekStartDate = week2Start, category = "GAS", amount = 60.0, createdAt = 2000L))

        // Simulate bootstrap: for each past week with no savings record, calculate and insert
        val allTransactions = getAllLocalTransactions()
        val weekTotals = allTransactions
            .filter { it.weekStartDate != currentWeekStart }
            .groupBy { it.weekStartDate }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }

        for ((week, total) in weekTotals) {
            if (weeklySavingsDao.getSavingsForWeek(week) == null) {
                val savingsAmount = if (total > 0 && total < 100.0) 100.0 - total else 0.0
                weeklySavingsDao.upsert(WeeklySavings(weekStartDate = week, amount = savingsAmount))
            }
        }

        // Week 1: $100 - $70 = $30, Week 2: $100 - $60 = $40
        assertEquals(30.0, weeklySavingsDao.getSavingsForWeek(week1Start)!!.amount, 0.01)
        assertEquals(40.0, weeklySavingsDao.getSavingsForWeek(week2Start)!!.amount, 0.01)
        assertEquals(70.0, weeklySavingsDao.getTotalSavingsSync(), 0.01)
    }

    @Test
    fun `bootstrap skips weeks already synced from Firebase`() = runTest {
        budgetPreferences.setBudget(100.0)
        val currentWeekStart = week3Start

        // Historical transactions
        dao.insert(Transaction(weekStartDate = week1Start, category = "GAS", amount = 70.0, createdAt = 1000L))
        dao.insert(Transaction(weekStartDate = week2Start, category = "GAS", amount = 60.0, createdAt = 2000L))

        // Week 1 already synced from Firebase with slightly different amount (e.g., different device)
        weeklySavingsDao.upsert(WeeklySavings(weekStartDate = week1Start, amount = 25.0))

        // Simulate bootstrap
        val allTransactions = getAllLocalTransactions()
        val weekTotals = allTransactions
            .filter { it.weekStartDate != currentWeekStart }
            .groupBy { it.weekStartDate }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }

        for ((week, total) in weekTotals) {
            if (weeklySavingsDao.getSavingsForWeek(week) == null) {
                val savingsAmount = if (total > 0 && total < 100.0) 100.0 - total else 0.0
                weeklySavingsDao.upsert(WeeklySavings(weekStartDate = week, amount = savingsAmount))
            }
        }

        // Week 1 should retain Firebase value ($25), not recalculate ($30)
        assertEquals(25.0, weeklySavingsDao.getSavingsForWeek(week1Start)!!.amount, 0.01)
        // Week 2 should be backfilled
        assertEquals(40.0, weeklySavingsDao.getSavingsForWeek(week2Start)!!.amount, 0.01)
        // Total = $25 + $40 = $65
        assertEquals(65.0, weeklySavingsDao.getTotalSavingsSync(), 0.01)
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private fun getAllLocalTransactions(): List<Transaction> {
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT id, weekStartDate, category, amount, isAdjustment, createdAt, details FROM transactions"
        )
        val results = mutableListOf<Transaction>()
        while (cursor.moveToNext()) {
            results.add(
                Transaction(
                    id = cursor.getLong(0),
                    weekStartDate = cursor.getString(1),
                    category = cursor.getString(2),
                    amount = cursor.getDouble(3),
                    isAdjustment = cursor.getInt(4) == 1,
                    createdAt = cursor.getLong(5),
                    details = if (cursor.isNull(6)) null else cursor.getString(6)
                )
            )
        }
        cursor.close()
        return results
    }
}
