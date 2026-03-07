package com.example.weeklytotals.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SplitSyncManager(context: Context) {

    private val db = FirebaseDatabase.getInstance()
    private val rootRef: DatabaseReference = db.getReference("weekly_totals").child("split")
    private val entriesRef: DatabaseReference = rootRef.child("entries")
    private val categoriesRef: DatabaseReference = rootRef.child("categories")

    private val appDb = AppDatabase.getInstance(context)
    private val entryDao = appDb.splitEntryDao()
    private val categoryDao = appDb.splitCategoryDao()

    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var suppressEntrySync = false
    @Volatile
    private var suppressCategorySync = false

    /** Completes after the first onDataChange from the entry listener. */
    val initialSyncComplete = CompletableDeferred<Unit>()

    companion object {
        private const val TAG = "SplitSyncManager"

        @Volatile
        private var INSTANCE: SplitSyncManager? = null

        fun getInstance(context: Context): SplitSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SplitSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ── Entry sync ──────────────────────────────────────────────────

    fun pushEntry(entry: SplitEntry) {
        if (suppressEntrySync) {
            Log.d(TAG, "pushEntry suppressed for ${entry.createdAt}")
            return
        }
        val key = entry.createdAt.toString()
        val data = mapOf<String, Any?>(
            "category" to entry.category,
            "amount" to entry.amount,
            "comment" to entry.comment,
            "splitType" to entry.splitType,
            "createdByEmail" to entry.createdByEmail,
            "createdAt" to entry.createdAt
        )
        Log.d(TAG, "Pushing entry $key to split/entries/")
        entriesRef.child(key).setValue(data)
            .addOnSuccessListener { Log.d(TAG, "Push entry $key SUCCESS") }
            .addOnFailureListener { e -> Log.e(TAG, "Push entry $key FAILED", e) }
    }

    fun deleteEntry(entry: SplitEntry) {
        if (suppressEntrySync) return
        val key = entry.createdAt.toString()
        entriesRef.child(key).removeValue()
            .addOnFailureListener { e -> Log.e(TAG, "Failed to delete split entry from Firebase", e) }
    }

    fun startEntryListener() {
        Log.d(TAG, "Starting entry listener on split/entries/")
        entriesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Entry listener onDataChange: ${snapshot.childrenCount} children")
                scope.launch {
                    suppressEntrySync = true
                    try {
                        syncEntriesFromFirebase(snapshot)
                    } finally {
                        suppressEntrySync = false
                        initialSyncComplete.complete(Unit)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Split entry listener cancelled: ${error.message}", error.toException())
                initialSyncComplete.complete(Unit)
            }
        })
    }

    private suspend fun syncEntriesFromFirebase(snapshot: DataSnapshot) {
        val remoteEntries = mutableMapOf<Long, Map<String, Any?>>()
        for (child in snapshot.children) {
            val createdAt = when (val raw = child.child("createdAt").value) {
                is Long -> raw
                is Double -> raw.toLong()
                else -> continue
            }
            remoteEntries[createdAt] = mapOf(
                "category" to (child.child("category").value as? String ?: continue),
                "amount" to (child.child("amount").value?.let { toDouble(it) } ?: continue),
                "comment" to (child.child("comment").value as? String ?: continue),
                "splitType" to (child.child("splitType").value as? String ?: continue),
                "createdByEmail" to (child.child("createdByEmail").value as? String ?: continue),
                "createdAt" to createdAt
            )
        }

        val localEntries = entryDao.getAllEntriesSync()
        val localByCreatedAt = localEntries.associateBy { it.createdAt }

        // Insert or update remote entries locally
        for ((createdAt, data) in remoteEntries) {
            val local = localByCreatedAt[createdAt]
            val category = data["category"] as String
            val amount = data["amount"] as Double
            val comment = data["comment"] as String
            val splitType = data["splitType"] as String
            val createdByEmail = data["createdByEmail"] as String

            if (local == null) {
                entryDao.insert(
                    SplitEntry(
                        category = category,
                        amount = amount,
                        comment = comment,
                        splitType = splitType,
                        createdByEmail = createdByEmail,
                        createdAt = createdAt
                    )
                )
            } else if (local.category != category ||
                local.amount != amount ||
                local.comment != comment ||
                local.splitType != splitType ||
                local.createdByEmail != createdByEmail
            ) {
                entryDao.update(
                    local.copy(
                        category = category,
                        amount = amount,
                        comment = comment,
                        splitType = splitType,
                        createdByEmail = createdByEmail
                    )
                )
            }
        }

        // Delete local entries not in Firebase
        if (remoteEntries.isNotEmpty()) {
            for (local in localEntries) {
                if (local.createdAt !in remoteEntries) {
                    entryDao.delete(local)
                }
            }
        }
    }

    // ── Category sync ───────────────────────────────────────────────

    fun pushCategory(category: SplitCategory) {
        if (suppressCategorySync) return
        val data = mapOf(
            "name" to category.name,
            "displayName" to category.displayName,
            "color" to category.color,
            "isSystem" to category.isSystem
        )
        categoriesRef.child(category.name).setValue(data)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to push split category", e) }
    }

    fun deleteCategory(category: SplitCategory) {
        if (suppressCategorySync) return
        categoriesRef.child(category.name).removeValue()
            .addOnFailureListener { e -> Log.e(TAG, "Failed to delete split category from Firebase", e) }
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
                Log.e(TAG, "Split category listener cancelled", error.toException())
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
                    SplitCategory(
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

        // Delete local categories not in Firebase (skip defaults)
        if (remoteCategories.isNotEmpty()) {
            for (local in localCategories) {
                if (local.name !in remoteCategories && local.name !in AppDatabase.DEFAULT_SPLIT_CATEGORY_NAMES) {
                    categoryDao.delete(local)
                }
            }
        }
    }

    // ── Initial upload ──────────────────────────────────────────────

    fun pushAllLocalData() {
        scope.launch {
            val entries = entryDao.getAllEntriesSync()
            for (e in entries) {
                pushEntry(e)
            }

            val categories = categoryDao.getAllCategoriesSync()
            for (c in categories) {
                pushCategory(c)
            }
        }
    }

    // ── Start all listeners ─────────────────────────────────────────

    fun startListening() {
        Log.d(TAG, "startListening() called — root: split/")
        pushAllLocalData()
        startEntryListener()
        startCategoryListener()
    }

    // ── Utility ─────────────────────────────────────────────────────

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
