package dev.debene.gandula.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import dev.debene.gandula.data.TeamRepository
import dev.debene.gandula.domain.Match
import dev.debene.gandula.domain.Team
import dev.debene.gandula.engine.MatchEngine

/**
 * Holds the loaded teams and the most recent simulated [Match]. The simulation
 * itself is a pure, synchronous call into [MatchEngine] — fast enough (a few
 * thousand RNG draws) to run on the main thread when the user taps "Jogar".
 */
class MatchViewModel(app: Application) : AndroidViewModel(app) {

    val teams: List<Team> = runCatching { TeamRepository.loadTeams(app) }.getOrDefault(emptyList())

    var homeIndex by mutableStateOf(0)
        private set
    var awayIndex by mutableStateOf(if (teams.size > 1) 1 else 0)
        private set
    var seedText by mutableStateOf("1998")
        private set
    var match by mutableStateOf<Match?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun selectHome(index: Int) { homeIndex = index }
    fun selectAway(index: Int) { awayIndex = index }
    fun setSeed(value: String) { seedText = value.filter { it.isDigit() }.take(18) }

    fun play() {
        error = null
        val home = teams.getOrNull(homeIndex)
        val away = teams.getOrNull(awayIndex)
        if (home == null || away == null) {
            error = "Selecione dois times."
            return
        }
        if (home.id == away.id) {
            error = "Escolha times diferentes."
            return
        }
        val seed = seedText.toLongOrNull() ?: 0L
        match = runCatching { MatchEngine.simulate(home, away, seed) }
            .onFailure { error = it.message ?: "Erro na simulação." }
            .getOrNull()
    }
}
