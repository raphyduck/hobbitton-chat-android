package com.garfiec.librechat.feature.tasks

import com.garfiec.librechat.core.data.scheduler.SchedulerRepository
import com.garfiec.librechat.core.model.scheduler.Consumption
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The usage page reads the spend over today, 7 and 30 days (11/11/2026, Raphaël: « les usages sur
 * 7 jours, mais également sur 24 heures et sur 30 jours »), shows the week first, and a window
 * whose read failed leaves the others standing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UsagePeriodsTest {

    private val scheduler = mockk<SchedulerRepository>().also {
        coEvery { it.isConfigured() } returns true
        coEvery { it.consumption(1) } returns Consumption(totalSpend = 0.5)
        coEvery { it.consumption(7) } returns Consumption(totalSpend = 4.0)
        coEvery { it.consumption(30) } throws IllegalStateException("gateway down")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the three windows are read and the week shows first`() {
        val viewModel = UsageViewModel(scheduler)
        val state = viewModel.state.value
        assertThat(state.loading).isFalse()
        assertThat(state.period).isEqualTo(UsagePeriod.WEEK)
        assertThat(state.consumption?.totalSpend).isEqualTo(4.0)
        assertThat(state.consumptions.keys).containsExactly(UsagePeriod.TODAY, UsagePeriod.WEEK)

        viewModel.selectPeriod(UsagePeriod.TODAY)
        assertThat(viewModel.state.value.consumption?.totalSpend).isEqualTo(0.5)

        // The 30 days failed: no report, never a zero.
        viewModel.selectPeriod(UsagePeriod.MONTH)
        assertThat(viewModel.state.value.consumption).isNull()
    }
}
