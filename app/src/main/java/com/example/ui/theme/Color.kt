package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Premium Navy Blue & White Palette
val NavyDeep = Color(0xFF070F1E)          // Deepest Navy background
val NavySurface = Color(0xFF0D1B34)       // Primary Navy surface
val NavySurfaceVariant = Color(0xFF152646)// Elevated Navy container
val NavyCard = Color(0xFF10203C)          // Navy card surface
val NavyBorder = Color(0xFF1E355B)        // Navy outline / border
val NavyAccent = Color(0xFF38BDF8)        // Radiant ice-blue / cyan highlight
val NavyBrand = Color(0xFF0284C7)         // Vivid Navy brand blue

val WhitePure = Color(0xFFFFFFFF)         // Pure crisp white
val WhiteOff = Color(0xFFF1F5F9)          // Soft white
val WhiteMuted = Color(0xFFE2E8F0)        // Light slate white

val LightNavyBackground = Color(0xFFF4F7FC) // Light mode clean white-navy canvas
val LightNavySurface = Color(0xFFFFFFFF)    // Crisp white surface
val LightNavySurfaceVariant = Color(0xFFE2E8F0)
val LightNavyBorder = Color(0xFFCBD5E1)
val LightNavyPrimary = Color(0xFF0A192F)    // Deep authoritative navy text/actions

val TextMutedDark = Color(0xFF94A3B8)      // Cool slate text for dark theme
val TextMutedLight = Color(0xFF475569)     // Slate text for light theme
val TextNavyDark = Color(0xFF0F172A)       // Deep navy text for light theme

// Backward compatible aliases
val CyberBlack = NavyDeep
val CyberDarkSurface = NavySurface
val CyberDarkSurfaceVariant = NavySurfaceVariant
val CyberDarkCard = NavyCard
val CyberBorderDark = NavyBorder

val CyberLightBackground = LightNavyBackground
val CyberLightSurface = LightNavySurface
val CyberLightSurfaceVariant = LightNavySurfaceVariant
val CyberLightCard = LightNavySurface
val CyberBorderLight = LightNavyBorder

val CyberWhite = WhitePure
val CyberOffWhite = WhiteOff
val CyberTextMutedDark = TextMutedDark
val CyberTextMutedLight = TextMutedLight
val CyberDeepDarkText = TextNavyDark

// Status & Threat Level Colors
val ThreatCritical = Color(0xFFEF4444) // Bright Red
val ThreatHigh = Color(0xFFF97316)     // Deep Orange
val ThreatModerate = Color(0xFFF59E0B) // Amber
val ThreatLow = Color(0xFF38BDF8)      // Ice Blue / Cyan
val ThreatSafe = Color(0xFF10B981)     // Emerald Green
val CyberElectricCyan = NavyAccent     // Dynamic Ice Blue
