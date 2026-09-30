package app.sprintstacker.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

@Entity(tableName = "blocks")
data class BlockEntity(
    @PrimaryKey val id: Long,
    val position: Int,
    val kind: String,
    val width: Float,
    val height: Float,
    val x: Float,
    val points: Int,
    val colorIndex: Int,
)

/** Single-row table (id = 0) holding score, settings and any sprint in progress. */
@Entity(tableName = "game_state")
data class GameStateEntity(
    @PrimaryKey val id: Int = 0,
    val score: Int = 0,
    val best: Int = 0,
    val bestHeight: Float = 0f,
    val preset: String = "FIVE_MIN",
    val completedSinceAd: Int = 0,
    val sprintPreset: String? = null,
    val sprintStartedAt: Long? = null,
    val stoppedWhileLocked: Boolean = false,
    val vibrate: Boolean = true,
)

@Dao
abstract class GameDao {
    @Query("SELECT * FROM blocks ORDER BY position")
    abstract suspend fun blocks(): List<BlockEntity>

    @Query("SELECT * FROM game_state WHERE id = 0")
    abstract suspend fun state(): GameStateEntity?

    @Upsert
    abstract suspend fun upsertState(state: GameStateEntity)

    @Query("DELETE FROM blocks")
    abstract suspend fun clearBlocks()

    @Insert
    abstract suspend fun insertBlocks(blocks: List<BlockEntity>)

    @Transaction
    open suspend fun saveAll(state: GameStateEntity, blocks: List<BlockEntity>) {
        upsertState(state)
        clearBlocks()
        insertBlocks(blocks)
    }
}

@Database(entities = [BlockEntity::class, GameStateEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
}
