package app.undertow.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Version 1 of the local store.
 *
 * Until the app has shipped there are no migrations to write; from the first release
 * onward every version bump needs a `Migration` plus a `MigrationTestHelper` test
 * against the exported schema in `core/database/schemas`.
 */
@Database(
    entities = [
        UsageEventEntity::class,
        ScrollSessionEntity::class,
        DailyTotalEntity::class,
        DailySummaryEntity::class,
        IngestRunEntity::class,
        ManualEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class UndertowDatabase : RoomDatabase() {

    abstract fun usageLogDao(): UsageLogDao

    abstract fun sessionDao(): SessionDao

    abstract fun rollupDao(): RollupDao

    abstract fun diagnosticsDao(): DiagnosticsDao

    abstract fun manualEntryDao(): ManualEntryDao

    companion object {
        const val NAME = "undertow.db"
    }
}
