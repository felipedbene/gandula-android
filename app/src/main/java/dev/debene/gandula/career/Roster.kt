package dev.debene.gandula.career

import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Team

/**
 * Resolves the user's effective team from the registry default plus the
 * `Career.userRoster` overlay (port of `web/src/util/roster.ts`). An empty
 * overlay falls back to the registry; a non-empty one wins, with the XI and
 * bench reconciled so a sold/retired player can't dangle in either.
 */
object Roster {

    /** The roster the working career currently presents — `userRoster` once
     *  populated, else the registry default. */
    fun workingRoster(career: Career, registry: Map<Int, Team>): List<Player> =
        if (career.userRoster.isEmpty()) registry.getValue(career.controlledTeamId).roster else career.userRoster

    /** Effective Team for the controlled side: base with the roster overlay
     *  swapped in (XI/bench reconciled) and the season tactics applied. */
    fun effectiveTeam(base: Team, userRoster: List<Player>, userTactics: SeasonTactics? = null): Team {
        var team = base
        if (userRoster.isNotEmpty()) {
            val xi = reconcileXI(base.startingXi, userRoster)
            val xiSet = xi.toSet()
            val rosterIds = userRoster.map { it.id }.toSet()
            team = team.copy(
                roster = userRoster,
                startingXi = xi,
                bench = base.bench.filter { it in rosterIds && it !in xiSet },
            )
        }
        if (userTactics != null) {
            team = team.copy(formation = userTactics.formation, tactics = userTactics.tactics)
        }
        return team
    }

    fun userTeam(career: Career, registry: Map<Int, Team>): Team =
        effectiveTeam(registry.getValue(career.controlledTeamId), career.userRoster, career.userTactics)

    private fun attrSum(p: Player): Int {
        val a = p.attributes
        return a.pace + a.technique + a.passing + a.defending + a.finishing + a.stamina
    }

    /** Base XI pruned to rostered ids, backfilled (best attrs, lower id breaks
     *  ties) until a fieldable 11. No-op when every starter is still rostered. */
    private fun reconcileXI(baseXi: List<Int>, roster: List<Player>): List<Int> {
        val rosterIds = roster.map { it.id }.toSet()
        val xi = baseXi.filter { it in rosterIds }.toMutableList()
        if (xi.size >= 11) return xi
        val used = xi.toMutableSet()
        val candidates = roster.filter { it.id !in used }
            .sortedWith(compareByDescending<Player> { attrSum(it) }.thenBy { it.id })
        for (p in candidates) {
            if (xi.size >= 11) break
            xi.add(p.id)
        }
        return xi
    }
}
