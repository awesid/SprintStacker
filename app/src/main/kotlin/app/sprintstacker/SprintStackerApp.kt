package app.sprintstacker

import android.app.Application
import androidx.room.Room
import app.sprintstacker.ads.AdsManager
import app.sprintstacker.data.AppDatabase
import app.sprintstacker.data.GameRepository

class SprintStackerApp : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "sprint-stacker.db").build()
    }
    val repository by lazy { GameRepository(database.gameDao()) }
    val ads by lazy { AdsManager(this) }
}
