package com.garfiec.librechat.shared.engine

import java.util.TimeZone

internal actual fun localUtcOffsetMillis(epochMillis: Long): Long =
    TimeZone.getDefault().getOffset(epochMillis).toLong()
