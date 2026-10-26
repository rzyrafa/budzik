package com.budzik.alarm.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// Soft Pastel & High-Contrast Minimalist Design System
// =========================================================================

// Backgrounds (warm light cream / off-white #F5F4EF)
val MainBgCream = Color(0xFFF5F4EF)       // Ciepły, jasny kremowy / złamana biel (#F5F4EF)
val MainBgLight = MainBgCream
val MainBgDark = Color(0xFF121214)        // Głęboki matowy grafit

// High-Contrast Elements (Floating dock, bottom sheets, primary action buttons)
val ContrastBlack = Color(0xFF0D0D0D)     // Czysta, głęboka czerń (#0D0D0D)
val ContrastWhite = Color(0xFFFFFFFF)     // Czysta biel (#FFFFFF)
val PillBgBeige = Color(0xFFE6E4DD)       // Jasny beż dla pastylek (#E6E4DD)
val DarkPillBg = Color(0xFF1C1C22)        // Ciemnoszary dla nieaktywnych pastylek w sheet (#1C1C22)
val DarkCardBg = Color(0xFF16161A)        // Gładki ciemny kafelek (#16161A)
val CardBgLight = Color(0xFFFFFFFF)       // Biała matowa karta
val CardBgDark = Color(0xFF0D0D0D)        // Głęboka czerń
val BorderSubtleLight = Color(0xFFE5E4DE)
val BorderSubtleDark = Color(0xFF2A2A2E)

// Text Colors
val TextPrimaryLight = Color(0xFF0D0D0D)
val TextSecondaryLight = Color(0xFF6E6E73)
val TextPrimaryDark = Color(0xFFF6F5F0)
val TextSecondaryDark = Color(0xFF8E8E93)
val TextSubtle = Color(0xFF8E8E93)

// Matte Pastel Accents (for series and active states)
// #1 pudrowy róż (#F4B8E4), #2 maślany żółty (#F9E784), #3 pastelowa zieleń (#CBE896), #4 błękit (#A8D5FC), #5 lawenda (#D3C4FC)
val PastelPink = Color(0xFFF4B8E4)        // Pudrowy róż (#F4B8E4)
val PastelYellow = Color(0xFFF9E784)      // Maślany żółty (#F9E784)
val PastelLime = Color(0xFFCBE896)        // Pastelowa zieleń / limonka (#CBE896)
val PastelBlue = Color(0xFFA8D5FC)        // Pastelowy błękit (#A8D5FC)
val PastelLavender = Color(0xFFD3C4FC)    // Lawendowy (#D3C4FC)

// Ordered series colors for stacked cards (#1-#5)
val PastelSeriesColors = listOf(
    PastelPink,
    PastelYellow,
    PastelLime,
    PastelBlue,
    PastelLavender
)

// Ordered pastel rotation for single alarm cards on main screen
val PastelSingleAlarmColors = listOf(
    PastelBlue,
    PastelPink,
    PastelYellow,
    PastelLime,
    PastelLavender
)


// Legacy compatibility aliases for Material Theme
val PrimaryLight = ContrastBlack
val OnPrimaryLight = ContrastWhite
val PrimaryContainerLight = PastelPink
val OnPrimaryContainerLight = ContrastBlack

val SecondaryLight = ContrastBlack
val OnSecondaryLight = ContrastWhite
val SecondaryContainerLight = PastelBlue
val OnSecondaryContainerLight = ContrastBlack

val TertiaryLight = PastelLime
val OnTertiaryLight = ContrastBlack
val TertiaryContainerLight = PastelLime
val OnTertiaryContainerLight = ContrastBlack

val BackgroundLight = MainBgLight
val OnBackgroundLight = TextPrimaryLight
val SurfaceLight = CardBgLight
val OnSurfaceLight = TextPrimaryLight
val SurfaceVariantLight = MainBgLight
val OnSurfaceVariantLight = TextSecondaryLight

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = ContrastWhite

val PrimaryDark = PastelPink
val OnPrimaryDark = ContrastBlack
val PrimaryContainerDark = ContrastBlack
val OnPrimaryContainerDark = ContrastWhite

val SecondaryDark = PastelBlue
val OnSecondaryDark = ContrastBlack
val SecondaryContainerDark = ContrastBlack
val OnSecondaryContainerDark = ContrastWhite

val TertiaryDark = PastelLime
val OnTertiaryDark = ContrastBlack
val TertiaryContainerDark = ContrastBlack
val OnTertiaryContainerDark = ContrastWhite

val BackgroundDark = MainBgDark
val OnBackgroundDark = TextPrimaryDark
val SurfaceDark = CardBgDark
val OnSurfaceDark = TextPrimaryDark
val SurfaceVariantDark = MainBgDark
val OnSurfaceVariantDark = TextSecondaryDark

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
