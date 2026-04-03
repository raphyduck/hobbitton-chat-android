package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.model.Balance

interface BalanceRepository {
    suspend fun getBalance(): Result<Balance>
}
