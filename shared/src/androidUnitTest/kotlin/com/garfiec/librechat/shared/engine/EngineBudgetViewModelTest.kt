package com.garfiec.librechat.shared.engine

import com.garfiec.librechat.core.data.scheduler.ChatBudgetSource
import com.garfiec.librechat.core.model.scheduler.ChatBudget
import com.garfiec.librechat.core.network.api.ChatBudgetRefused
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Les plafonds des conversations : ce qui est lu, ce qui part, et ce qui ne part pas. */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineBudgetViewModelTest {

    /** Le planificateur en mémoire, qui compte ses écritures et peut les refuser. */
    private class FakeSource(
        var stored: ChatBudget? = ChatBudget(day = 15.0, week = 45.0, month = 200.0),
        var readFails: Boolean = false,
        var refusal: ChatBudgetRefused? = null,
    ) : ChatBudgetSource {
        val writes = mutableListOf<ChatBudget>()

        override suspend fun chatBudget(): ChatBudget? {
            if (readFails) error("planificateur injoignable")
            return stored
        }

        override suspend fun setChatBudget(day: Double, week: Double, month: Double): ChatBudget {
            refusal?.let { throw it }
            return ChatBudget(day, week, month).also {
                writes += it
                stored = it
            }
        }
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
    fun `the editor opens on what the gateway applies`() {
        val state = EngineBudgetViewModel(FakeSource(ChatBudget(day = 12.5, week = 45.0, month = 200.0))).state.value

        assertThat(state.loading).isFalse()
        assertThat(state.day).isEqualTo("12.5")
        assertThat(state.week).isEqualTo("45")
        assertThat(state.month).isEqualTo("200")
    }

    @Test
    fun `without a scheduler, or one from before the route, there is nothing to edit`() {
        assertThat(EngineBudgetViewModel(null).state.value.unavailable).isTrue()
        assertThat(EngineBudgetViewModel(FakeSource(stored = null)).state.value.unavailable).isTrue()
    }

    @Test
    fun `a failed read can be tried again`() {
        val source = FakeSource(readFails = true)
        val vm = EngineBudgetViewModel(source)
        assertThat(vm.state.value.loadFailed).isTrue()

        source.readFails = false
        vm.load()

        assertThat(vm.state.value.loadFailed).isFalse()
        assertThat(vm.state.value.week).isEqualTo("45")
    }

    @Test
    fun `new amounts leave, the decimal comma read as a point`() {
        val source = FakeSource()
        val vm = EngineBudgetViewModel(source)

        vm.setDay("20,5")
        vm.setWeek("60")
        vm.setMonth("250")
        vm.save()

        assertThat(source.writes).containsExactly(ChatBudget(day = 20.5, week = 60.0, month = 250.0))
        assertThat(vm.state.value.saved).isTrue()
    }

    @Test
    fun `amounts out of order or not amounts never leave the phone`() {
        val source = FakeSource()
        val vm = EngineBudgetViewModel(source)

        vm.setDay("50")
        vm.save()
        assertThat(vm.state.value.problem).isEqualTo(BudgetProblem.DAY_OVER_WEEK)

        vm.setDay("15")
        vm.setMonth("30")
        vm.save()
        assertThat(vm.state.value.problem).isEqualTo(BudgetProblem.WEEK_OVER_MONTH)

        vm.setMonth("0")
        vm.save()
        assertThat(vm.state.value.problem).isEqualTo(BudgetProblem.INVALID_AMOUNT)

        vm.setMonth("beaucoup")
        vm.save()
        assertThat(vm.state.value.problem).isEqualTo(BudgetProblem.INVALID_AMOUNT)
        assertThat(source.writes).isEmpty()
    }

    @Test
    fun `nothing changed closes without writing`() {
        val source = FakeSource()
        val vm = EngineBudgetViewModel(source)

        vm.save()

        assertThat(vm.state.value.saved).isTrue()
        assertThat(source.writes).isEmpty()
    }

    @Test
    fun `a refusal is shown in the scheduler's words and the screen stays`() {
        val source = FakeSource(refusal = ChatBudgetRefused(400, "« mois » dépasse 1000 $, la borne du serveur"))
        val vm = EngineBudgetViewModel(source)

        vm.setMonth("5000")
        vm.save()

        assertThat(vm.state.value.saved).isFalse()
        assertThat(vm.state.value.refusal).isEqualTo("« mois » dépasse 1000 $, la borne du serveur")
        assertThat(vm.state.value.month).isEqualTo("5000")
    }
}
