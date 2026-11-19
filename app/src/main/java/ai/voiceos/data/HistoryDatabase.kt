package ai.voiceos.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import ai.voiceos.core.ActionFailure
import ai.voiceos.core.ActionResult
import ai.voiceos.core.CommandResult
import ai.voiceos.core.DeviceAction
import kotlinx.coroutines.flow.Flow

internal enum class InputSource(val key: String) { VOICE("voice"), TEXT("text"), SHORTCUT("shortcut") }

/** Transcript-free metadata. No microphone data, nickname or arbitrary input is written here. */
@Entity(tableName = "action_history")
internal data class StoredAction(
    @PrimaryKey val id: String,
    val occurredAt: Long,
    val actionKey: String,
    val resultKey: String,
    val batteryPercent: Int?,
    val sourceKey: String,
) {
    fun toResult(): CommandResult.Executed? {
        val action = DeviceAction.fromKey(actionKey) ?: return null
        val result = when (resultKey) {
            DISPATCHED -> if (action != DeviceAction.BATTERY) ActionResult.Dispatched else return null
            BATTERY -> batteryPercent?.takeIf { it in 0..100 && action == DeviceAction.BATTERY }
                ?.let(ActionResult::Battery) ?: return null
            else -> ActionFailure.fromKey(resultKey)?.let(ActionResult::Failed) ?: return null
        }
        return CommandResult.Executed(action, result)
    }

    companion object {
        const val DISPATCHED = "intent-dispatched"
        const val BATTERY = "battery-reading"
        const val LIMIT = 40

        fun from(id: String, at: Long, source: InputSource, executed: CommandResult.Executed): StoredAction {
            val result = executed.result
            val status = when (result) {
                ActionResult.Dispatched -> DISPATCHED
                is ActionResult.Battery -> BATTERY
                is ActionResult.Failed -> result.cause.key
            }
            return StoredAction(id, at, executed.action.key, status, (result as? ActionResult.Battery)?.percent, source.key)
        }
    }
}

@Dao
internal abstract class HistoryDao {
    @Query("SELECT * FROM action_history ORDER BY occurredAt DESC, id DESC LIMIT ${StoredAction.LIMIT}")
    abstract fun observe(): Flow<List<StoredAction>>

    @Insert
    protected abstract suspend fun insert(row: StoredAction)

    @Query("DELETE FROM action_history WHERE id NOT IN (SELECT id FROM action_history ORDER BY occurredAt DESC, id DESC LIMIT ${StoredAction.LIMIT})")
    protected abstract suspend fun prune()

    @Transaction
    open suspend fun record(row: StoredAction) {
        insert(row)
        prune()
    }

    @Query("DELETE FROM action_history")
    abstract suspend fun clear()
}

// v1 ships without migrations. Turn on schema export and retain versioned snapshots before adding a v2.
@Database(entities = [StoredAction::class], version = 1, exportSchema = false)
internal abstract class HistoryDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
}
