package dev.debene.gandula.ui

import dev.debene.gandula.domain.Formation
import dev.debene.gandula.domain.Mentality
import dev.debene.gandula.domain.Pressing
import dev.debene.gandula.domain.Tempo
import dev.debene.gandula.domain.Width

/** Portuguese display labels for the tactic enums — the UI never shows the raw
 *  English enum constant names (Attacking/Fast/High/Wide). */

fun Formation.ptLabel(): String = when (this) {
    Formation.F442 -> "4-4-2"
    Formation.F433 -> "4-3-3"
    Formation.F352 -> "3-5-2"
    Formation.F4231 -> "4-2-3-1"
}

fun Mentality.ptLabel(): String = when (this) {
    Mentality.VeryDefensive -> "Muito defensivo"
    Mentality.Defensive -> "Defensivo"
    Mentality.Balanced -> "Equilibrado"
    Mentality.Attacking -> "Ofensivo"
    Mentality.VeryAttacking -> "Muito ofensivo"
}

fun Tempo.ptLabel(): String = when (this) {
    Tempo.Slow -> "Lento"
    Tempo.Normal -> "Normal"
    Tempo.Fast -> "Rápido"
}

fun Pressing.ptLabel(): String = when (this) {
    Pressing.Low -> "Baixa"
    Pressing.Medium -> "Média"
    Pressing.High -> "Alta"
}

fun Width.ptLabel(): String = when (this) {
    Width.Narrow -> "Fechado"
    Width.Normal -> "Normal"
    Width.Wide -> "Aberto"
}
