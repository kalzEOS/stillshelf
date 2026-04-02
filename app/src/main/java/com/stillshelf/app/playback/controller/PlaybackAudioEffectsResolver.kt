package com.stillshelf.app.playback.controller

internal fun resolveBoostGainMb(boostLevel: Float): Int {
    return (boostLevel.coerceIn(0f, 1f) * 1800f).toInt().coerceIn(0, 2000)
}

internal fun resolveSoftToneBandLevel(
    softToneLevel: Float,
    bandIndex: Int,
    bandCount: Int,
    minLevelMb: Int,
    maxLevelMb: Int
): Short {
    val safeBandCount = bandCount.coerceAtLeast(1)
    val safeBandIndex = bandIndex.coerceIn(0, safeBandCount - 1)
    val ratio = if (safeBandCount <= 1) 0f else safeBandIndex.toFloat() / (safeBandCount - 1).toFloat()
    val attenuationWeight = ((ratio - 0.35f) / 0.65f).coerceIn(0f, 1f)
    val attenuationMb = (softToneLevel.coerceIn(0f, 1f) * attenuationWeight * 900f).toInt()
    return (0 - attenuationMb).coerceIn(minLevelMb, maxLevelMb).toShort()
}
