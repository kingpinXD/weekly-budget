package com.example.weeklytotals.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirebaseSyncManager(context: Context) {

    private val db = FirebaseDatabase.getInstance()
    private val rootRef: DatabaseReference = db.getReference("weekly_totals")
    private val transactionsRef: DatabaseReference = rootRef.child("transactions")
    private val categoriesRef: DatabaseReference = rootRef.child("categories")
    private val budgetRef: DatabaseReference = rootRef.child("budget")

    private val appDb = AppDatabase.getInstance(context)
    private val transactionDao = appDb.transactionDao()
    private val categoryDao = appDb.categoryDao()
    private val budgetPreferences = BudgetPreferences(context)

    private val scope = CoroutineScope(Dispatchers.IO)

    // Flags to prevent infinite sync loops.
    // When we write to Room from a Firebase listener, we set these so the observer
    // does not push the same change back to Firebase.
    @Volatile
    private var suppressTransactionSync = false
    @Volatile
    private var suppressCategorySync = false

    companion object {
        private const val TAG = "FirebaseSyncManager"

        @Volatile
        private var INSTANCE: FirebaseSyncManager? = null

        fun getInstance(context: Context): FirebaseSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ── Transaction sync ────────────────────────────────────────────────

    fun pushTransaction(transaction: Transaction) {
        if (suppressTransactionSync) return
        val key = transaction.createdAt.toString()
        val data = mapOf(
            "weekStartDate" to transaction.weekStartDate,
            "category" to transaction.category,
            "amount" to transaction.amount,
            "isAdjustment" to transaction.isAdjustment,
            "createdAt" to transaction.createdAt
        )
        transactionsRef.child(key).setValue(data)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to push transaction", e) }
    }

    fun deleteTransaction(transaction: Transaction) {
        if (suppressTransactionSync) return
        val key = transaction.createdAt.toString()
        transactionsRef.child(key).removeValue()
            .addOnFailureListener { e -> Log.e(TAG, "Failed to delete transaction from Firebase", e) }
    }

    fun startTransactionListener() {
        transactionsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    suppressTransactionSync = true
                    try {
                        syncTransactionsFromFirebase(snapshot)
                    } finally {
                        suppressTransactionSync = false
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Transaction listener cancelled", error.toException())
            }
        })
    }

    private suspend fun syncTransactionsFromFirebase(snapshot: DataSnapshot) {
        // Build a set of createdAt timestamps from Firebase
        val remoteTransactions = mutableMapOf<Long, Map<String, Any?>>()
        for (child in snapshot.children) {
            val createdAt = when (val raw = child.child("createdAt").value) {
                is Long -> raw
                is Double -> raw.toLong()
                else -> continue
            }
            remoteTransactions[createdAt] = mapOf(
                "weekStartDate" to (child.child("weekStartDate").value as? String ?: continue),
                "category" to (child.child("category").value as? String ?: continue),
                "amount" to (child.child("amount").value?.let { toDouble(it) } ?: continue),
                "isAdjustment" to (child.child("isAdjustment").value as? Boolean ?: false),
                "createdAt" to createdAt
            )
        }

        // Get all local transactions across all weeks using a raw query
        val localTransactions = getAllLocalTransactions()
        val localByCreatedAt = localTransactions.associateBy { it.createdAt }

        // Insert or update transactions from Firebase that are missing or different locally
        for ((createdAt, data) in remoteTransactions) {
            val local = localByCreatedAt[createdAt]
            val weekStartDate = data["weekStartDate"] as String
            val category = data["category"] as String
            val amount = data["amount"] as Double
            val isAdjustment = data["isAdjustment"] as Boolean

            if (local == null) {
                if (isAdjustment) {
                    // Special handling: at most one adjustment per week.
                    // A local adjustment may already exist (from checkWeekRollover)
                    // with a different createdAt. Update it to align with remote.
                    val existingAdjustment = transactionDao.getAdjustmentForWeek(weekStartDate)
                    if (existingAdjustment != null) {
                        transactionDao.update(
                            existingAdjustment.copy(
                                amount = amount,
                                createdAt = createdAt
                            )
                        )
                    } else {
                        transactionDao.insert(
                            Transaction(
                                weekStartDate = weekStartDate,
                                category = category,
                                amount = amount,
                                isAdjustment = isAdjustment,
                                createdAt = createdAt
                            )
                        )
                    }
                } else {
                    // New non-adjustment transaction from remote - insert locally
                    transactionDao.insert(
                        Transaction(
                            weekStartDate = weekStartDate,
                            category = category,
                            amount = amount,
                            isAdjustment = isAdjustment,
                            createdAt = createdAt
                        )
                    )
                }
            } else if (local.weekStartDate != weekStartDate ||
                local.category != category ||
                local.amount != amount ||
                local.isAdjustment != isAdjustment
            ) {
                // Transaction exists but fields differ - update locally
                transactionDao.update(
                    local.copy(
                        weekStartDate = weekStartDate,
                        category = category,
                        amount = amount,
                        isAdjustment = isAdjustment
                    )
                )
            }
        }

        // Collect weeks that have adjustments in Firebase, so we don't
        // accidentally delete a local adjustment whose createdAt was just re-aligned.
        val remoteAdjustmentWeeks = remoteTransactions.values
            .filter { it["isAdjustment"] as? Boolean == true }
            .mapTo(mutableSetOf()) { it["weekStartDate"] as String }

        // Delete local transactions that are not in Firebase
        // Skip if Firebase is empty — avoids race condition on first sync / fresh DB
        if (remoteTransactions.isNotEmpty()) {
            for (local in localTransactions) {
                if (local.createdAt !in remoteTransactions) {
                    // Don't delete a local adjustment if Firebase still has one
                    // for the same week (its createdAt may have been re-aligned above)
                    if (local.isAdjustment && local.weekStartDate in remoteAdjustmentWeeks) {
                        continue
                    }
                    transactionDao.delete(local)
                }
            }
        }
    }

    private fun getAllLocalTransactions(): List<Transaction> {
        val cursor = appDb.openHelper.readableDatabase.query(
            "SELECT id, weekStartDate, category, amount, isAdjustment, createdAt FROM transactions"
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
                    createdAt = cursor.getLong(5)
                )
            )
        }
        cursor.close()
        return results
    }

    // ── Category sync ───────────────────────────────────────────────────

    fun pushCategory(category: CategoryEntity) {
        if (suppressCategorySync) return
        val data = mapOf(
            "name" to category.name,
            "displayName" to category.displayName,
            "color" to category.color,
            "isSystem" to category.isSystem
        )
        categoriesRef.child(category.name).setValue(data)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to push category", e) }
    }

    fun deleteCategory(category: CategoryEntity) {
        if (suppressCategorySync) return
        categoriesRef.child(category.name).removeValue()
            .addOnFailureListener { e -> Log.e(TAG, "Failed to delete category from Firebase", e) }
    }

    fun startCategoryListener() {
        categoriesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                scope.launch {
                    suppressCategorySync = true
                    try {
                        syncCategoriesFromFirebase(snapshot)
                    } finally {
                        suppressCategorySync = false
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Category listener cancelled", error.toException())
            }
        })
    }

    private suspend fun syncCategoriesFromFirebase(snapshot: DataSnapshot) {
        val remoteCategories = mutableMapOf<String, Map<String, Any?>>()
        for (child in snapshot.children) {
            val name = child.child("name").value as? String ?: continue
            remoteCategories[name] = mapOf(
                "name" to name,
                "displayName" to (child.child("displayName").value as? String ?: name),
                "color" to (child.child("color").value as? String ?: "#607D8B"),
                "isSystem" to (child.child("isSystem").value as? Boolean ?: false)
            )
        }

        val localCategories = categoryDao.getAllCategoriesSync()
        val localByName = localCategories.associateBy { it.name }

        // Insert or update remote categories locally
        for ((name, data) in remoteCategories) {
            val local = localByName[name]
            val displayName = data["displayName"] as String
            val color = data["color"] as String
            val isSystem = data["isSystem"] as Boolean

            if (local == null) {
                categoryDao.insert(
                    CategoryEntity(
                        name = name,
                        displayName = displayName,
                        color = color,
                        isSystem = isSystem
                    )
                )
            } else if (local.displayName != displayName ||
                local.color != color ||
                local.isSystem != isSystem
            ) {
                categoryDao.update(
                    local.copy(
                        displayName = displayName,
                        color = color,
                        isSystem = isSystem
                    )
                )
            }
        }

        // Delete local categories not in Firebase
        // Skip if Firebase is empty — avoids race condition on first sync / fresh DB
        if (remoteCategories.isNotEmpty()) {
            for (local in localCategories) {
                if (local.name !in remoteCategories) {
                    categoryDao.delete(local)
                }
            }
        }
    }

    // ── Budget sync ─────────────────────────────────────────────────────

    fun pushBudget(amount: Double, isSet: Boolean) {
        val data = mapOf(
            "amount" to amount,
            "isSet" to isSet
        )
        budgetRef.setValue(data)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to push budget", e) }
    }

    fun startBudgetListener() {
        budgetRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val amount = snapshot.child("amount").value?.let { toDouble(it) } ?: return
                val isSet = snapshot.child("isSet").value as? Boolean ?: return

                if (isSet) {
                    val currentBudget = budgetPreferences.getBudget()
                    val currentIsSet = budgetPreferences.isBudgetSet()
                    if (currentBudget != amount || !currentIsSet) {
                        budgetPreferences.setBudget(amount)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Budget listener cancelled", error.toException())
            }
        })
    }

    // ── Initial upload ──────────────────────────────────────────────────

    /**
     * Push all local data to Firebase. Called once at startup so that if this device
     * has data and Firebase is empty, it populates the remote.
     */
    fun pushAllLocalData() {
        scope.launch {
            // Push all local transactions
            val transactions = getAllLocalTransactions()
            for (t in transactions) {
                pushTransaction(t)
            }

            // Push all local categories
            val categories = categoryDao.getAllCategoriesSync()
            for (c in categories) {
                pushCategory(c)
            }

            // Push budget
            if (budgetPreferences.isBudgetSet()) {
                pushBudget(budgetPreferences.getBudget(), true)
            }
        }
    }

    // ── Start all listeners ─────────────────────────────────────────────

    fun startListening() {
        pushAllLocalData()
        startTransactionListener()
        startCategoryListener()
        startBudgetListener()
    }

    // ── Reset ─────────────────────────────────────────────────────────

    /**
     * Clears all data from Firebase RTDB (transactions, categories, budget).
     * Other synced devices will pick up the deletion via their listeners.
     */
    fun clearAllData(onComplete: (() -> Unit)? = null) {
        rootRef.removeValue()
            .addOnSuccessListener { onComplete?.invoke() }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to clear Firebase data", e)
                onComplete?.invoke()
            }
    }

    // ── Utility ─────────────────────────────────────────────────────────

    private fun toDouble(value: Any): Double {
        return when (value) {
            is Double -> value
            is Long -> value.toDouble()
            is Float -> value.toDouble()
            is Int -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}
