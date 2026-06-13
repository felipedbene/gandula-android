package dev.debene.gandula

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.debene.gandula.career.CareerEngine
import dev.debene.gandula.data.CareerStore
import dev.debene.gandula.data.TeamRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Save/load round-trip for the single-slot career store (needs a Context). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CareerStoreTest {

    @Test
    fun `career survives save then load via re-simulation`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val teams = TeamRepository.loadTeams(app)
        val registry = teams.associateBy { it.id }
        CareerStore.clear(app)
        assertFalse(CareerStore.hasSave(app))

        var career = CareerEngine.newCareer(teams, seed = 1998, random = Random(5))
        // Play a few rounds so the reveal cursor (and accrued cash) is non-trivial.
        repeat(7) { career = CareerEngine.revealNextRound(career, registry) }
        CareerStore.save(app, career)
        assertTrue(CareerStore.hasSave(app))

        val loaded = CareerStore.load(app, registry)!!
        assertEquals(career.seed, loaded.seed)
        assertEquals(career.controlledTeamId, loaded.controlledTeamId)
        assertEquals(career.money, loaded.money)
        assertEquals(career.season.year, loaded.season.year)
        assertEquals(career.season.currentRoundIdx, loaded.season.currentRoundIdx)
        // Re-simulation reproduces identical standings byte-for-byte.
        assertEquals(
            career.season.divisions.map { it.record.standings },
            loaded.season.divisions.map { it.record.standings },
        )
    }
}
