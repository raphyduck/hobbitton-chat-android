package com.librechat.android.core.data.repository

import com.librechat.android.core.common.result.Result
import com.librechat.android.core.common.result.safeApiCall
import com.librechat.android.core.model.Balance
import com.librechat.android.core.network.api.BalanceApi

class BalanceRepositoryImpl(
    private val balanceApi: BalanceApi,
) : BalanceRepository {

    override suspend fun getBalance(): Result<Balance> = safeApiCall {
        balanceApi.getBalance()
    }
}
