package dev.debene.gandula.career

import dev.debene.gandula.domain.Attributes
import dev.debene.gandula.domain.Player
import dev.debene.gandula.domain.Position
import dev.debene.gandula.domain.Team
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * The transfer market — port of `web/src/util/transfer-market.ts`. A
 * deterministic per-(careerSeed, year) pool of free agents you buy to strengthen
 * the squad, with an age-curve pricing model and buy/sell guards. Buying adds to
 * `Career.userRoster` (debiting cash); the bigger squad both plays and costs
 * wages next season — the spend→strength→results→cash loop.
 *
 * Deferred from upstream: transfer history records, and the regen/youth pipeline
 * (opponent evolution isn't ported).
 */
object TransferMarket {

    // ─── Pool composition + id layout ────────────────────────────────────────
    val POOL_COMPOSITION = linkedMapOf(
        Position.GK to 2, Position.DEF to 4, Position.MID to 4, Position.FWD to 2,
    )
    const val POOL_SIZE = 12
    const val FREE_AGENT_ID_BASE = 900_000
    const val FREE_AGENT_ID_YEAR_STRIDE = 1000

    // ─── Pricing / roster bounds ─────────────────────────────────────────────
    const val BUY_MULTIPLIER = 1.0
    const val SELL_MULTIPLIER = 0.7
    const val MIN_ROSTER = 14
    const val MAX_ROSTER = 25

    const val ELITE_AGENT_FRACTION = 0.12
    const val ELITE_ATTR_CAP = 92

    /** Base offset for regen/youth ids (E.2.b) — far above free agents; `regenId`
     *  partitions by team/year/slot so generated youths never collide. */
    const val REGEN_ID_BASE = 2_000_000_000

    /** Deterministic, collision-free id for a regen youth. */
    fun regenId(teamId: Int, yearOffset: Int, slot: Int): Int =
        REGEN_ID_BASE + teamId * 10_000 + yearOffset * 100 + slot

    private val FIRST_NAMES = listOf(
        "Carlos", "José", "Roberto", "Marcos", "Paulo", "Ricardo", "André",
        "Fernando", "Lucas", "Gabriel", "Rafael", "Bruno", "Felipe", "Diego",
        "Eduardo", "Henrique", "Tiago", "Vinícius", "Leandro", "Rogério",
        "Cláudio", "Sérgio", "Alex", "Daniel", "Júlio", "Murilo", "Pedro",
        "Mateus", "Thiago", "Wesley",
    )
    private val LAST_NAMES = listOf(
        "Silva", "Santos", "Oliveira", "Souza", "Lima", "Pereira", "Costa",
        "Almeida", "Rodrigues", "Ferreira", "Carvalho", "Gomes", "Martins",
        "Araújo", "Ribeiro", "Cardoso", "Barbosa", "Rocha", "Dias", "Mendes",
        "Castro", "Cunha", "Andrade", "Moraes", "Pinto", "Teixeira", "Borges",
        "Moreira", "Vieira", "Nogueira",
    )

    /** Deterministic free-agent pool for (careerSeed, year). Draw order mirrors
     *  upstream exactly so the pool is reproducible across save/load. */
    fun generateFreeAgents(careerSeed: Long, year: Int): List<Player> {
        val poolSeed = careerSeed xor year.toLong() xor 0xfa1fL
        val rng = Mulberry32(poolSeed.toInt())
        val idBase = FREE_AGENT_ID_BASE + (year - CareerEngine.FIRST_YEAR) * FREE_AGENT_ID_YEAR_STRIDE
        val players = ArrayList<Player>(POOL_SIZE)
        var slot = 0
        for ((position, count) in POOL_COMPOSITION) {
            repeat(count) {
                players.add(buildFreeAgent(rng, idBase + slot, position))
                slot++
            }
        }
        return players
    }

    private fun generateName(rng: Mulberry32): String {
        val first = FIRST_NAMES[(rng.next() * FIRST_NAMES.size).toInt()]
        val last = LAST_NAMES[(rng.next() * LAST_NAMES.size).toInt()]
        return "$first $last"
    }

    private fun scaleByPosition(rng: Mulberry32, position: Position): Attributes {
        val elite = rng.next() < ELITE_AGENT_FRACTION
        val cap = if (elite) ELITE_ATTR_CAP else 85
        fun base(): Int = if (elite) 62 + (rng.next() * 25).toInt() else 30 + (rng.next() * 41).toInt()
        fun boost(n: Int, bonus: Int) = min(cap, n + bonus)
        var pace = base(); var technique = base(); var passing = base()
        var defending = base(); var finishing = base(); val stamina = base()
        when (position) {
            Position.GK -> { defending = boost(defending, 15); finishing = max(10, finishing - 20) }
            Position.DEF -> { defending = boost(defending, 10); pace = boost(pace, 5) }
            Position.MID -> { passing = boost(passing, 5); technique = boost(technique, 5) }
            Position.FWD -> { finishing = boost(finishing, 15); pace = boost(pace, 5) }
        }
        return Attributes(pace, technique, passing, defending, finishing, stamina)
    }

    private fun buildFreeAgent(rng: Mulberry32, id: Int, position: Position): Player {
        val name = generateName(rng)
        val age = 18 + (rng.next() * 17).toInt() // [18, 34]
        return Player(id, name, age, position, scaleByPosition(rng, position))
    }

    /** A regen youth (age 16–19) for a position — same attribute generator as free
     *  agents (they start modest and grow via aging's sub-23 bump). */
    internal fun buildYouth(rng: Mulberry32, id: Int, position: Position): Player {
        val name = generateName(rng)
        val age = 16 + (rng.next() * 4).toInt() // [16, 19]
        return Player(id, name, age, position, scaleByPosition(rng, position))
    }

    // ─── Scouting + pricing ──────────────────────────────────────────────────
    private fun avgAttrs(p: Player): Double {
        val a = p.attributes
        return (a.pace + a.technique + a.passing + a.defending + a.finishing + a.stamina) / 6.0
    }

    fun playerOverall(p: Player): Int = Math.round(avgAttrs(p)).toInt()

    data class ScoutReport(
        val overall: Int,
        val samePositionCount: Int,
        val positionAvg: Int,
        val delta: Int,
        val rank: Int,
    )

    fun scoutReport(player: Player, roster: List<Player>): ScoutReport {
        val overall = playerOverall(player)
        val samePos = roster.filter { it.position == player.position }
        if (samePos.isEmpty()) return ScoutReport(overall, 0, 0, 0, 1)
        val positionAvg = Math.round(samePos.sumOf { playerOverall(it) }.toDouble() / samePos.size).toInt()
        val better = samePos.count { playerOverall(it) > overall }
        return ScoutReport(overall, samePos.size, positionAvg, overall - positionAvg, better + 1)
    }

    private fun ageMultiplier(age: Int): Double = when {
        age < 21 -> 1.5
        age < 26 -> 1.3
        age < 30 -> 1.0
        age < 33 -> 0.7
        else -> 0.4
    }

    fun playerPrice(player: Player, kind: Kind): Long {
        val avg = avgAttrs(player)
        val base = (avg * avg * 100 * ageMultiplier(player.age)).roundToLong()
        return (base * if (kind == Kind.BUY) BUY_MULTIPLIER else SELL_MULTIPLIER).roundToLong()
    }

    enum class Kind { BUY, SELL }

    // ─── Guards ──────────────────────────────────────────────────────────────
    sealed interface CheckResult {
        data object Ok : CheckResult
        data class No(val reason: String) : CheckResult
        val ok: Boolean get() = this is Ok
    }

    fun canBuy(career: Career, registry: Map<Int, Team>, price: Long): CheckResult {
        val roster = Roster.workingRoster(career, registry)
        if (roster.size >= MAX_ROSTER) return CheckResult.No("Elenco cheio ($MAX_ROSTER)")
        if (career.money < price) return CheckResult.No("Dinheiro insuficiente")
        return CheckResult.Ok
    }

    fun canSell(career: Career, registry: Map<Int, Team>, playerId: Int): CheckResult {
        val team = Roster.userTeam(career, registry)
        if (Roster.workingRoster(career, registry).size <= MIN_ROSTER) {
            return CheckResult.No("Elenco mínimo ($MIN_ROSTER)")
        }
        if (playerId in team.startingXi) return CheckResult.No("Está no XI titular")
        return CheckResult.Ok
    }

    // ─── Mutations (caller gates with can*) ──────────────────────────────────
    fun buy(career: Career, registry: Map<Int, Team>, player: Player): Career {
        val price = playerPrice(player, Kind.BUY)
        return career.copy(
            money = career.money - price,
            userRoster = Roster.workingRoster(career, registry) + player,
            transfers = career.transfers + TransferRecord("buy", player.name, player.position, price),
        )
    }

    fun sell(career: Career, registry: Map<Int, Team>, player: Player): Career {
        val price = playerPrice(player, Kind.SELL)
        return career.copy(
            money = career.money + price,
            userRoster = Roster.workingRoster(career, registry).filter { it.id != player.id },
            transfers = career.transfers + TransferRecord("sell", player.name, player.position, price),
        )
    }

    /** The pool players not already on the squad (sold/bought ones disappear). */
    fun availableAgents(career: Career, registry: Map<Int, Team>): List<Player> {
        val rosterIds = Roster.workingRoster(career, registry).map { it.id }.toSet()
        return generateFreeAgents(career.seed, career.season.year).filter { it.id !in rosterIds }
    }
}
