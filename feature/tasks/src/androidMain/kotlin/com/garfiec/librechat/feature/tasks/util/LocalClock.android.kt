package com.garfiec.librechat.feature.tasks.util

import java.util.Calendar

actual fun currentHourOfDay(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
