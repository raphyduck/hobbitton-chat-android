package com.garfiec.librechat.core.data.lock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockTest {

    private class FakeStore : AppLockStore {
        var stored: String? = null
        var failureCount = 0
        var bio = false
        var delay = AppLock.DEFAULT_DELAY_MILLIS
        override fun hasCode() = stored != null
        override fun setCode(code: String) { stored = code }
        override fun checkCode(code: String) = stored == code
        override fun failures() = failureCount
        override fun setFailures(count: Int) { failureCount = count }
        override fun biometrics() = bio
        override fun setBiometrics(enabled: Boolean) { bio = enabled }
        override fun delayMillis() = delay
        override fun setDelayMillis(millis: Long) { delay = millis }
        override fun clear() { stored = null; failureCount = 0; bio = false; delay = AppLock.DEFAULT_DELAY_MILLIS }
    }

    private var now = 0L
    private val store = FakeStore()
    private val lock = AppLock(store, clock = { now }, io = Dispatchers.Unconfined)

    @Test
    fun noCodeAsksForOneThenACodeLocksEveryStart() = runTest {
        lock.start()
        assertEquals(LockState.UNSET, lock.state.value)
        lock.create("123456")
        assertEquals(LockState.UNLOCKED, lock.state.value)

        val restarted = AppLock(store, clock = { now }, io = Dispatchers.Unconfined)
        restarted.start()
        assertEquals(LockState.LOCKED, restarted.state.value)
    }

    @Test
    fun aShortAbsenceStaysOpenALongerOneLocks() = runTest {
        lock.start()
        lock.create("123456")
        lock.onBackground()
        now += AppLock.DEFAULT_DELAY_MILLIS - 1
        lock.onForeground()
        assertEquals(LockState.UNLOCKED, lock.state.value)

        lock.onBackground()
        now += AppLock.DEFAULT_DELAY_MILLIS
        lock.onForeground()
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun atOnceLocksOnEveryReturn() = runTest {
        lock.start()
        lock.create("123456")
        lock.setDelay(0)
        lock.onBackground()
        lock.onForeground()
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun wrongCodesCountDownAndARightOneResetsTheCount() = runTest {
        store.stored = "123456"
        lock.start()
        assertEquals(UnlockResult.Wrong(4), lock.unlock("000000"))
        assertEquals(UnlockResult.Wrong(3), lock.unlock("111111"))
        assertEquals(UnlockResult.Unlocked, lock.unlock("123456"))
        assertEquals(0, store.failureCount)
    }

    @Test
    fun theFifthWrongCodeForgetsTheLock() = runTest {
        store.stored = "123456"
        store.bio = true
        lock.start()
        repeat(AppLock.MAX_ATTEMPTS - 1) { lock.unlock("000000") }
        assertEquals(UnlockResult.TooManyAttempts, lock.unlock("000000"))
        assertFalse(store.hasCode())
        assertFalse(lock.biometrics.value)
        // Locked until the sign-out forgets it: no new code can be set meanwhile.
        assertEquals(LockState.LOCKED, lock.state.value)
        assertEquals(UnlockResult.Wrong(4), lock.unlock("123456"))
    }

    @Test
    fun theFailuresSurviveARestart() = runTest {
        store.stored = "123456"
        lock.start()
        repeat(3) { lock.unlock("000000") }
        val restarted = AppLock(store, clock = { now }, io = Dispatchers.Unconfined)
        restarted.start()
        assertEquals(UnlockResult.Wrong(1), restarted.unlock("000000"))
    }

    @Test
    fun theFingerprintOpensOnlyWhenTurnedOn() = runTest {
        store.stored = "123456"
        lock.start()
        lock.unlockWithBiometrics()
        assertEquals(LockState.LOCKED, lock.state.value)
        lock.setBiometrics(true)
        lock.unlockWithBiometrics()
        assertEquals(LockState.UNLOCKED, lock.state.value)
    }

    @Test
    fun onlySixDigitsMakeACode() {
        assertTrue(AppLock.isValidCode("012345"))
        assertFalse(AppLock.isValidCode("12345"))
        assertFalse(AppLock.isValidCode("12345a"))
        assertFalse(AppLock.isValidCode("1234567"))
    }

    @Test
    fun signingOutForgetsTheCode() = runTest {
        lock.start()
        lock.create("123456")
        lock.forget()
        assertEquals(LockState.UNSET, lock.state.value)
        assertFalse(store.hasCode())
    }

    @Test
    fun theFirstGuessesAreRefused() {
        listOf("000000", "111111", "123456", "987654", "456789").forEach { assertTrue(AppLock.isTooSimple(it), it) }
        listOf("135790", "121212", "240917").forEach { assertFalse(AppLock.isTooSimple(it), it) }
    }
}
