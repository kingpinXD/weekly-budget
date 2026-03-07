package com.example.weeklytotals.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile TransactionDao _transactionDao;

  private volatile CategoryDao _categoryDao;

  private volatile WeeklySavingsDao _weeklySavingsDao;

  private volatile SplitEntryDao _splitEntryDao;

  private volatile SplitCategoryDao _splitCategoryDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(6) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `weekStartDate` TEXT NOT NULL, `category` TEXT NOT NULL, `amount` REAL NOT NULL, `isAdjustment` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `details` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `displayName` TEXT NOT NULL, `color` TEXT NOT NULL, `isSystem` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `weekly_savings` (`weekStartDate` TEXT NOT NULL, `amount` REAL NOT NULL, PRIMARY KEY(`weekStartDate`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `split_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `amount` REAL NOT NULL, `comment` TEXT NOT NULL, `splitType` TEXT NOT NULL, `createdByEmail` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `split_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `displayName` TEXT NOT NULL, `color` TEXT NOT NULL, `isSystem` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '157363a042ce6151260d36ce5b0e2714')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `transactions`");
        db.execSQL("DROP TABLE IF EXISTS `categories`");
        db.execSQL("DROP TABLE IF EXISTS `weekly_savings`");
        db.execSQL("DROP TABLE IF EXISTS `split_entries`");
        db.execSQL("DROP TABLE IF EXISTS `split_categories`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsTransactions = new HashMap<String, TableInfo.Column>(7);
        _columnsTransactions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("weekStartDate", new TableInfo.Column("weekStartDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("isAdjustment", new TableInfo.Column("isAdjustment", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("details", new TableInfo.Column("details", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTransactions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTransactions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTransactions = new TableInfo("transactions", _columnsTransactions, _foreignKeysTransactions, _indicesTransactions);
        final TableInfo _existingTransactions = TableInfo.read(db, "transactions");
        if (!_infoTransactions.equals(_existingTransactions)) {
          return new RoomOpenHelper.ValidationResult(false, "transactions(com.example.weeklytotals.data.Transaction).\n"
                  + " Expected:\n" + _infoTransactions + "\n"
                  + " Found:\n" + _existingTransactions);
        }
        final HashMap<String, TableInfo.Column> _columnsCategories = new HashMap<String, TableInfo.Column>(5);
        _columnsCategories.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("color", new TableInfo.Column("color", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("isSystem", new TableInfo.Column("isSystem", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCategories = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCategories = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCategories = new TableInfo("categories", _columnsCategories, _foreignKeysCategories, _indicesCategories);
        final TableInfo _existingCategories = TableInfo.read(db, "categories");
        if (!_infoCategories.equals(_existingCategories)) {
          return new RoomOpenHelper.ValidationResult(false, "categories(com.example.weeklytotals.data.CategoryEntity).\n"
                  + " Expected:\n" + _infoCategories + "\n"
                  + " Found:\n" + _existingCategories);
        }
        final HashMap<String, TableInfo.Column> _columnsWeeklySavings = new HashMap<String, TableInfo.Column>(2);
        _columnsWeeklySavings.put("weekStartDate", new TableInfo.Column("weekStartDate", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWeeklySavings.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWeeklySavings = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesWeeklySavings = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoWeeklySavings = new TableInfo("weekly_savings", _columnsWeeklySavings, _foreignKeysWeeklySavings, _indicesWeeklySavings);
        final TableInfo _existingWeeklySavings = TableInfo.read(db, "weekly_savings");
        if (!_infoWeeklySavings.equals(_existingWeeklySavings)) {
          return new RoomOpenHelper.ValidationResult(false, "weekly_savings(com.example.weeklytotals.data.WeeklySavings).\n"
                  + " Expected:\n" + _infoWeeklySavings + "\n"
                  + " Found:\n" + _existingWeeklySavings);
        }
        final HashMap<String, TableInfo.Column> _columnsSplitEntries = new HashMap<String, TableInfo.Column>(7);
        _columnsSplitEntries.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSplitEntries.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSplitEntries.put("amount", new TableInfo.Column("amount", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSplitEntries.put("comment", new TableInfo.Column("comment", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSplitEntries.put("splitType", new TableInfo.Column("splitType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSplitEntries.put("createdByEmail", new TableInfo.Column("createdByEmail", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSplitEntries.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSplitEntries = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSplitEntries = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSplitEntries = new TableInfo("split_entries", _columnsSplitEntries, _foreignKeysSplitEntries, _indicesSplitEntries);
        final TableInfo _existingSplitEntries = TableInfo.read(db, "split_entries");
        if (!_infoSplitEntries.equals(_existingSplitEntries)) {
          return new RoomOpenHelper.ValidationResult(false, "split_entries(com.example.weeklytotals.data.SplitEntry).\n"
                  + " Expected:\n" + _infoSplitEntries + "\n"
                  + " Found:\n" + _existingSplitEntries);
        }
        final HashMap<String, TableInfo.Column> _columnsSplitCategories = new HashMap<String, TableInfo.Column>(5);
        _columnsSplitCategories.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSplitCategories.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSplitCategories.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSplitCategories.put("color", new TableInfo.Column("color", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSplitCategories.put("isSystem", new TableInfo.Column("isSystem", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSplitCategories = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSplitCategories = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSplitCategories = new TableInfo("split_categories", _columnsSplitCategories, _foreignKeysSplitCategories, _indicesSplitCategories);
        final TableInfo _existingSplitCategories = TableInfo.read(db, "split_categories");
        if (!_infoSplitCategories.equals(_existingSplitCategories)) {
          return new RoomOpenHelper.ValidationResult(false, "split_categories(com.example.weeklytotals.data.SplitCategory).\n"
                  + " Expected:\n" + _infoSplitCategories + "\n"
                  + " Found:\n" + _existingSplitCategories);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "157363a042ce6151260d36ce5b0e2714", "76739903a8b1e699d971e49c0c27cc6b");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "transactions","categories","weekly_savings","split_entries","split_categories");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `transactions`");
      _db.execSQL("DELETE FROM `categories`");
      _db.execSQL("DELETE FROM `weekly_savings`");
      _db.execSQL("DELETE FROM `split_entries`");
      _db.execSQL("DELETE FROM `split_categories`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(TransactionDao.class, TransactionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CategoryDao.class, CategoryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(WeeklySavingsDao.class, WeeklySavingsDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SplitEntryDao.class, SplitEntryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SplitCategoryDao.class, SplitCategoryDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public TransactionDao transactionDao() {
    if (_transactionDao != null) {
      return _transactionDao;
    } else {
      synchronized(this) {
        if(_transactionDao == null) {
          _transactionDao = new TransactionDao_Impl(this);
        }
        return _transactionDao;
      }
    }
  }

  @Override
  public CategoryDao categoryDao() {
    if (_categoryDao != null) {
      return _categoryDao;
    } else {
      synchronized(this) {
        if(_categoryDao == null) {
          _categoryDao = new CategoryDao_Impl(this);
        }
        return _categoryDao;
      }
    }
  }

  @Override
  public WeeklySavingsDao weeklySavingsDao() {
    if (_weeklySavingsDao != null) {
      return _weeklySavingsDao;
    } else {
      synchronized(this) {
        if(_weeklySavingsDao == null) {
          _weeklySavingsDao = new WeeklySavingsDao_Impl(this);
        }
        return _weeklySavingsDao;
      }
    }
  }

  @Override
  public SplitEntryDao splitEntryDao() {
    if (_splitEntryDao != null) {
      return _splitEntryDao;
    } else {
      synchronized(this) {
        if(_splitEntryDao == null) {
          _splitEntryDao = new SplitEntryDao_Impl(this);
        }
        return _splitEntryDao;
      }
    }
  }

  @Override
  public SplitCategoryDao splitCategoryDao() {
    if (_splitCategoryDao != null) {
      return _splitCategoryDao;
    } else {
      synchronized(this) {
        if(_splitCategoryDao == null) {
          _splitCategoryDao = new SplitCategoryDao_Impl(this);
        }
        return _splitCategoryDao;
      }
    }
  }
}
