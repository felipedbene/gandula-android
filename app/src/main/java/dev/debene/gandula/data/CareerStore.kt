package dev.debene.gandula.data

import android.content.Context
import dev.debene.gandula.career.Career
import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.career.Deals
import dev.debene.gandula.career.SeasonHistory
import dev.debene.gandula.career.SeasonTactics
import dev.debene.gandula.career.TransferRecord
import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Team
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.File

/**
 * Single-slot career persistence — faithful to the web app's single IndexedDB
 * save slot. Rather than persist the full pre-simulated season (megabytes of
 * match logs), we store only the lightweight, deterministic state and
 * re-simulate the divisions on load. Same seed + same tier membership ⇒
 * identical season, so this round-trips exactly.
 */
object CareerStore {
    private const val FILE_NAME = "career.json"

    /** Serializable shape (schema v1 of the Android port). */
    @JsonClass(generateAdapter = true)
    data class CareerSave(
        val schemaVersion: Int,
        val seed: Long,
        val controlledTeamId: Int,
        val money: Long,
        val stadiumCapacity: Int,
        val fanbase: Int,
        val marketingMomentum: Int,
        val fired: Boolean,
        val year: Int,
        val tierIds: List<List<Int>>,
        val currentRoundIdx: Int,
        val history: List<SeasonHistory>,
        // schema v2: transfer-market squad overlay. Defaulted so v1 saves parse.
        val userRoster: List<Player> = emptyList(),
        // schema v3: season tactics, signed deals, current-season transfers.
        val userTactics: SeasonTactics? = null,
        val activeDeals: Deals? = null,
        val transfers: List<TransferRecord> = emptyList(),
        // schema v4: half-time tactics overrides, keyed by round (string for JSON).
        val halftimeTactics: Map<String, SeasonTactics> = emptyMap(),
    )

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(CareerSave::class.java)

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun hasSave(context: Context): Boolean = file(context).exists()

    fun save(context: Context, career: Career) {
        val s = career.season
        val saveData = CareerSave(
            schemaVersion = 4,
            seed = career.seed,
            controlledTeamId = career.controlledTeamId,
            money = career.money,
            stadiumCapacity = career.stadiumCapacity,
            fanbase = career.fanbase,
            marketingMomentum = career.marketingMomentum,
            fired = career.fired,
            year = s.year,
            tierIds = s.divisions.map { it.teamIds },
            currentRoundIdx = s.currentRoundIdx,
            history = career.history,
            userRoster = career.userRoster,
            userTactics = career.userTactics,
            activeDeals = career.activeDeals,
            transfers = career.transfers,
            halftimeTactics = career.halftimeTactics.mapKeys { it.key.toString() },
        )
        file(context).writeText(adapter.toJson(saveData))
    }

    /** Load and re-simulate, or null if no save / corrupt. */
    fun load(context: Context, registry: Map<Int, Team>): Career? {
        val f = file(context)
        if (!f.exists()) return null
        val saveData = runCatching { adapter.fromJson(f.readText()) }.getOrNull() ?: return null
        val halftime = saveData.halftimeTactics.mapKeys { it.key.toInt() }
        val season = CareerEngine.buildSeason(
            saveData.seed, saveData.year, saveData.tierIds, registry,
            saveData.controlledTeamId, saveData.userRoster, saveData.userTactics, halftime,
        ).copy(currentRoundIdx = saveData.currentRoundIdx)
        return Career(
            seed = saveData.seed,
            controlledTeamId = saveData.controlledTeamId,
            money = saveData.money,
            stadiumCapacity = saveData.stadiumCapacity,
            fanbase = saveData.fanbase,
            marketingMomentum = saveData.marketingMomentum,
            season = season,
            history = saveData.history,
            fired = saveData.fired,
            userRoster = saveData.userRoster,
            userTactics = saveData.userTactics,
            activeDeals = saveData.activeDeals,
            transfers = saveData.transfers,
            halftimeTactics = halftime,
        )
    }

    fun clear(context: Context) {
        file(context).delete()
    }
}
