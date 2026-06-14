package dev.debene.ui.theme

import androidx.compose.ui.graphics.Color

// Gandula palette — "frosted-glass / neon console": deep slate background, glowing
// indigo+purple accents, translucent glass surfaces, emerald/amber/rose status.

// ── Core surfaces ──────────────────────────────────────────────────────────
val SlateBg = Color(0xFF020617)        // app background (slate-950)
val SlateElevated = Color(0xFF0B1222)  // legible card surface
val SlateElevated2 = Color(0xFF141C2E) // raised / variant
val GlassBody = Color(0x14FFFFFF)      // translucent glass fill (8% white)
val GlassBodyActive = Color(0x1FFFFFFF)
val GlassBorder = Color(0x29FFFFFF)    // soft glowing frosted border
val SlateNavBg = Color(0xE6060A18)     // frosted bottom-nav (90% slate)

// ── Glow spots (radial, behind content) ────────────────────────────────────
val GlowIndigo = Color(0x4D16A34A)     // 30% green-600 (primary accent glow)
val GlowPurple = Color(0x339333EA)     // 20% purple-600

// ── Accents + typography ───────────────────────────────────────────────────
val IndigoLight = Color(0xFF4ADE80)    // primary accent — green-400
val PurpleLight = Color(0xFFC084FC)    // purple-300 (secondary)
val TextSlate100 = Color(0xFFF1F5F9)
val TextSlate400 = Color(0xFF94A3B8)
val TextSlate600 = Color(0xFF475569)

// ── Status ─────────────────────────────────────────────────────────────────
val StatusEmerald = Color(0xFF34D399)
val StatusGold = Color(0xFFFBBF24)
val StatusRose = Color(0xFFFB7185)

// ── Back-compat aliases (existing screens reference these names) ────────────
val ElectricBlue = IndigoLight
val ElectricBlueDim = Color(0xFF22C55E) // green-500
val NeonCyan = IndigoLight
val Ink = SlateBg
val Surface1 = SlateElevated
val Surface2 = SlateElevated2
val OutlineDim = Color(0xFF1E293B)
val TextHigh = TextSlate100
val TextMid = TextSlate400
val TextLow = TextSlate600
val BlueContainer = Color(0xFF0C2A18)   // deep green container
val OnBlueContainer = Color(0xFFD6FFE3) // light green
val PositiveGreen = StatusEmerald
val DangerRed = StatusRose
val DangerContainer = Color(0xFF3B151C)
val GoldTertiary = StatusGold
val GoldContainer = Color(0xFF2A2410)

// Hero gradient → green-600 → purple-600
val GradientStart = Color(0xFF16A34A)
val GradientEnd = Color(0xFF9333EA)
