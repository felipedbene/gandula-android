package dev.debene.gandula

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.career.Roster
import dev.debene.gandula.career.SeasonTactics
import dev.debene.gandula.career.TransferMarket
import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Mentality
import dev.debene.gandula.domain.Pressing
import dev.debene.gandula.domain.Tactics
import dev.debene.gandula.domain.Team
import dev.debene.gandula.domain.Tempo
import dev.debene.gandula.domain.Width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Mid-season interventions: a transfer or a pre-kickoff tactic change takes effect
 * from the current round forward, leaves already-revealed rounds untouched, and
 * reproduces exactly on the load path (the deterministic-replay invariant).
 */
class MidSeasonTest {

    private fun loadTeams(): List<Team> {
        val file = listOf(File("src/main/assets/teams.json"), File("app/src/main/assets/teams.json")).first { it.exists() }
        val type = Types.newParameterizedType(List::class.java, Team::class.java)
        return Moshi.Builder().build().adapter<List<Team>>(type).fromJson(file.readText()) ?: emptyList()
    }

    @Test
    fun `mid-season buy keeps played rounds intact and joins the squad from that round on`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        var career = CareerEngine.newCareer(teams, seed = 2026, random = Random(9))
        repeat(5) { career = CareerEngine.revealNextRound(career, registry) }
        assertEquals(5, career.season.currentRoundIdx)

        val before = CareerEngine.userDivision(career.season, career.controlledTeamId).record.matches.toList()

        val agent = TransferMarket.availableAgents(career, registry).maxByOrNull { TransferMarket.playerOverall(it) }!!
        val bought = CareerEngine.rebuildSeason(TransferMarket.buy(career, registry, agent), registry)

        // Roster timeline: absent at round 4, present from round 5 (the buy round).
        val start = Roster.seasonStartRoster(bought, registry)
        assertFalse(Roster.rosterAtRound(start, bought.transfers, 4).any { it.id == agent.id })
        assertTrue(Roster.rosterAtRound(start, bought.transfers, 5).any { it.id == agent.id })

        // Already-revealed rounds (0..4) are byte-identical; the buy only steers ahead.
        val divAfter = CareerEngine.userDivision(bought.season, bought.controlledTeamId)
        divAfter.record.fixtures.forEachIndexed { i, f ->
            if (f.round < 5) assertEquals("round ${f.round} must be untouched", before[i], divAfter.record.matches[i])
        }
    }

    @Test
    fun `a mid-season buy reproduces exactly on the load path`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        var career = CareerEngine.newCareer(teams, seed = 77, random = Random(3))
        repeat(6) { career = CareerEngine.revealNextRound(career, registry) }
        val agent = TransferMarket.availableAgents(career, registry).first()
        val bought = CareerEngine.rebuildSeason(TransferMarket.buy(career, registry, agent), registry)

        val reloaded = CareerEngine.buildSeason(
            bought.seed, bought.season.year, bought.season.divisions.map { it.teamIds }, registry,
            bought.controlledTeamId, bought.userRoster, bought.userTactics, bought.transfers,
        )
        val a = CareerEngine.userDivision(bought.season, bought.controlledTeamId)
        val b = CareerEngine.userDivision(reloaded, bought.controlledTeamId)
        assertEquals("transfer-steered matches reproduce on load", a.record.matches, b.record.matches)
    }

    @Test
    fun `a custom starting XI takes the pitch, changes the season, and reproduces on load`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        val career = CareerEngine.newCareer(teams, seed = 2026, random = Random(4))

        val team = Roster.userTeam(career, registry)
        val rosterList = Roster.workingRoster(career, registry)
        val byId = rosterList.associateBy { it.id }
        // A valid 3-5-2 lineup (1 GK + 3 DEF + 5 MID + 2 FWD), distinct from the default.
        val formation = dev.debene.gandula.domain.Formation.F352
        val customXi = Roster.lineupFor(rosterList, formation, emptyList())
        assertEquals("exactly one keeper", 1, customXi.count { byId[it]?.position == dev.debene.gandula.domain.Position.GK })
        val override = SeasonTactics(formation, team.tactics, customXi)

        val custom = CareerEngine.rebuildSeason(CareerEngine.setTactics(career, override), registry)

        // The chosen XI is actually fielded (set-equal — order may differ).
        val effective = Roster.userTeam(custom, registry)
        assertEquals("the custom XI takes the pitch", customXi.toSet(), effective.startingXi.toSet())

        // The season differs from the default XI (the lineup change took effect).
        val a0 = CareerEngine.userDivision(career.season, career.controlledTeamId).record.matches
        val a1 = CareerEngine.userDivision(custom.season, custom.controlledTeamId).record.matches
        assertTrue("a different XI yields a different season", a0 != a1)

        // …and reproduces exactly on the load path.
        val reloaded = CareerEngine.buildSeason(
            custom.seed, custom.season.year, custom.season.divisions.map { it.teamIds }, registry,
            custom.controlledTeamId, custom.userRoster, custom.userTactics,
        )
        assertEquals(
            "custom-XI season reproduces on load",
            a1,
            CareerEngine.userDivision(reloaded, custom.controlledTeamId).record.matches,
        )
    }

    @Test
    fun `lineupFor repairs an invalid XI to one keeper and valid counts`() {
        val team = loadTeams().first { it.roster.count { p -> p.position == dev.debene.gandula.domain.Position.GK } >= 2 }
        val roster = team.roster
        val byId = roster.associateBy { it.id }
        val gks = roster.filter { it.position == dev.debene.gandula.domain.Position.GK }.map { it.id }
        // A deliberately broken lineup: two keepers up front.
        val broken = (gks.take(2) + roster.filter { it.position != dev.debene.gandula.domain.Position.GK }.map { it.id }.take(9))
        val fixed = Roster.lineupFor(roster, dev.debene.gandula.domain.Formation.F433, broken)
        assertEquals("exactly one keeper", 1, fixed.count { byId[it]?.position == dev.debene.gandula.domain.Position.GK })
        assertEquals("eleven players", 11, fixed.size)
        assertEquals("no duplicates", 11, fixed.toSet().size)
    }

    @Test
    fun `pre-kickoff tactics steer that round and reproduce on the load path`() {
        val teams = loadTeams()
        val registry = teams.associateBy { it.id }
        val career = CareerEngine.newCareer(teams, seed = 555, random = Random(1))
        val override = SeasonTactics(Formation.F4231, Tactics(Mentality.VeryAttacking, Tempo.Fast, Pressing.High, Width.Wide))

        val withTac = CareerEngine.setMatchTactics(career, registry, round = 3, tactics = override)
        assertEquals(override, withTac.matchTactics[3])

        val reloaded = CareerEngine.buildSeason(
            withTac.seed, withTac.season.year, withTac.season.divisions.map { it.teamIds }, registry,
            withTac.controlledTeamId, withTac.userRoster, withTac.userTactics,
            matchTactics = mapOf(3 to override),
        )
        val a = CareerEngine.userDivision(withTac.season, withTac.controlledTeamId)
        val b = CareerEngine.userDivision(reloaded, withTac.controlledTeamId)
        assertEquals("pre-match-steered matches reproduce on load", a.record.matches, b.record.matches)
    }
}
