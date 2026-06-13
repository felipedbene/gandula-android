package dev.debene.gandula.engine

import dev.debene.gandula.domain.NearMissKind
import dev.debene.gandula.rng.MatchRng

/**
 * Broadcaster-style narration with templated phrasings. Port of upstream
 * `core/src/engine/narration.rs`. Selection is RNG-driven so output is
 * deterministic for a given seed. Tone: short, punchy transmissão de rádio.
 */
object Narration {

    /** Per-event context for phrasing selection. `scoreDiff` is from the event's
     * side perspective: +N leading by N, -N trailing. Build it AFTER any score
     * increment so `scoreDiff == 0` on a goal means "this goal just equalized". */
    class Context(val minute: Int, val scoreDiff: Int) {
        fun isLate(): Boolean = minute >= 85
    }

    fun shotWide(rng: MatchRng, minute: Int, shooter: String): String =
        when (rng.rangeU32(0, 3)) {
            0 -> "$minute' $shooter arrisca de longe... pra fora!"
            1 -> "$minute' $shooter chuta forte... passou ao lado!"
            else -> "$minute' $shooter tenta e isola a bola!"
        }

    fun shotSaved(rng: MatchRng, minute: Int, shooter: String, keeper: String): String =
        when (rng.rangeU32(0, 4)) {
            0 -> "$minute' $shooter chuta no gol... defendeu $keeper!"
            1 -> "$minute' Que defesa! $keeper pega o chute de $shooter!"
            2 -> "$minute' $shooter pega firme... e $keeper mostra reflexo!"
            else -> "$minute' Susto! $keeper segura o chute de $shooter."
        }

    fun goal(
        ctx: Context,
        rng: MatchRng,
        minute: Int,
        team: String,
        scorer: String,
        assist: String?,
    ): String {
        if (ctx.isLate() && ctx.scoreDiff == 0) {
            return when (rng.rangeU32(0, 2)) {
                0 -> "$minute' GOOOOL no fim! $scorer salva o $team!"
                else -> "$minute' É EMPATE! $scorer marca pro $team nos acréscimos!"
            }
        }
        if (ctx.isLate() && ctx.scoreDiff == 1) {
            return when (rng.rangeU32(0, 2)) {
                0 -> "$minute' GOOOL DE $team! $scorer desempata no fim!"
                else -> "$minute' $scorer marca no apagar das luzes pro $team!"
            }
        }
        return if (assist != null) {
            when (rng.rangeU32(0, 3)) {
                0 -> "$minute' GOOOL do $team! $scorer aproveita o passe de $assist!"
                1 -> "$minute' É DO $team! $assist encontra $scorer, que não perdoa!"
                else -> "$minute' $scorer marca pro $team! Assistência de $assist."
            }
        } else {
            when (rng.rangeU32(0, 3)) {
                0 -> "$minute' GOOOL do $team! $scorer balança a rede!"
                1 -> "$minute' É DO $team! $scorer acerta o cantinho!"
                else -> "$minute' $scorer marca pro $team! Pegou sozinho."
            }
        }
    }

    fun foul(rng: MatchRng, minute: Int, offender: String, victim: String): String =
        when (rng.rangeU32(0, 3)) {
            0 -> "$minute' Falta de $offender em $victim."
            1 -> "$minute' Entrada dura de $offender sobre $victim."
            else -> "$minute' $offender derruba $victim. Falta marcada."
        }

    fun yellow(rng: MatchRng, minute: Int, offender: String): String =
        when (rng.rangeU32(0, 2)) {
            0 -> "$minute' Cartão amarelo para $offender."
            else -> "$minute' Amarelo! $offender entra no relatório."
        }

    fun red(rng: MatchRng, minute: Int, offender: String): String =
        when (rng.rangeU32(0, 2)) {
            0 -> "$minute' VERMELHO! $offender expulso de campo!"
            else -> "$minute' EXPULSÃO! $offender deixa o jogo direto!"
        }

    fun penaltyAwarded(ctx: Context, rng: MatchRng, minute: Int, taker: String): String {
        if (ctx.isLate() && ctx.scoreDiff <= 0) {
            return when (rng.rangeU32(0, 2)) {
                0 -> "$minute' PÊNALTI! Decisivo, no fim! $taker vai pra cobrança..."
                else -> "$minute' PÊNALTI NO FIM! Tudo nas mãos de $taker..."
            }
        }
        return when (rng.rangeU32(0, 3)) {
            0 -> "$minute' Pênalti! O árbitro aponta para a marca. $taker pega a bola..."
            1 -> "$minute' PÊNALTI! $taker se prepara para a cobrança..."
            else -> "$minute' É pênalti! $taker ajeita a bola na marca da cal..."
        }
    }

    fun penaltyMissed(rng: MatchRng, minute: Int, taker: String, keeper: String): String =
        when (rng.rangeU32(0, 4)) {
            0 -> "$minute' PEGOU! $keeper defende o pênalti de $taker!"
            1 -> "$minute' QUE DEFESA! $keeper voa e agarra a cobrança de $taker!"
            2 -> "$minute' $taker bate fraco... $keeper pega sem dificuldade."
            else -> "$minute' PRA FORA! $taker manda por cima do gol!"
        }

    fun penaltyScored(ctx: Context, rng: MatchRng, minute: Int, team: String, taker: String): String {
        if (ctx.isLate() && ctx.scoreDiff == 0) {
            return when (rng.rangeU32(0, 2)) {
                0 -> "$minute' GOOOL DE PÊNALTI! $taker empata pro $team no fim!"
                else -> "$minute' É EMPATE! $taker converte a cobrança nos acréscimos!"
            }
        }
        if (ctx.isLate() && ctx.scoreDiff == 1) {
            return when (rng.rangeU32(0, 2)) {
                0 -> "$minute' GOOOL DE PÊNALTI! $taker desempata pro $team!"
                else -> "$minute' $taker bate firme da marca da cal! $team VIRA NO FIM!"
            }
        }
        return when (rng.rangeU32(0, 3)) {
            0 -> "$minute' GOOOL! $taker converte o pênalti pro $team!"
            1 -> "$minute' BALANÇA A REDE! $taker bate firme no canto. Gol de $team!"
            else -> "$minute' $taker cobra com categoria! Gol de pênalti pro $team!"
        }
    }

    fun nearMiss(rng: MatchRng, minute: Int, shooter: String, kind: NearMissKind): String =
        when (kind) {
            NearMissKind.Post -> when (rng.rangeU32(0, 3)) {
                0 -> "$minute' NA TRAVE! $shooter carimbou o poste!"
                1 -> "$minute' QUE ISSO! $shooter bate firme e a bola explode na trave!"
                else -> "$minute' NA MADEIRA! $shooter acerta o pé da trave!"
            }
            NearMissKind.Crossbar -> when (rng.rangeU32(0, 3)) {
                0 -> "$minute' NO TRAVESSÃO! $shooter bate por cima e a bola volta!"
                1 -> "$minute' QUASE! $shooter acerta o travessão e a bola sai!"
                else -> "$minute' $shooter chuta e a bola explode no travessão!"
            }
            NearMissKind.JustWide -> when (rng.rangeU32(0, 3)) {
                0 -> "$minute' QUASE! $shooter chuta rente à trave!"
                1 -> "$minute' Passou raspando! $shooter quase marca!"
                else -> "$minute' $shooter chuta com perigo... passou perto demais!"
            }
        }

    fun substitution(rng: MatchRng, minute: Int, team: String, off: String, on: String): String =
        when (rng.rangeU32(0, 4)) {
            0 -> "$minute' Substituição no $team: sai $off, entra $on."
            1 -> "$minute' Mexe o $team: sai $off, entra $on."
            2 -> "$minute' $team mexe: $off dá lugar a $on."
            else -> "$minute' O técnico tira $off e coloca $on no $team."
        }

    fun halfTime(rng: MatchRng, home: String, homeGoals: Int, away: String, awayGoals: Int): String =
        when (rng.rangeU32(0, 2)) {
            0 -> "45' Fim do primeiro tempo. $home ${homeGoals}x$awayGoals $away."
            else -> "45' Apito! Fim de primeira etapa: $home ${homeGoals}x$awayGoals $away."
        }

    fun fullTime(
        rng: MatchRng,
        minute: Int,
        home: String,
        homeGoals: Int,
        away: String,
        awayGoals: Int,
    ): String =
        when (rng.rangeU32(0, 2)) {
            0 -> "$minute' Fim de jogo. $home ${homeGoals}x$awayGoals $away."
            else -> "$minute' Apito final! $home ${homeGoals}x$awayGoals $away."
        }
}
