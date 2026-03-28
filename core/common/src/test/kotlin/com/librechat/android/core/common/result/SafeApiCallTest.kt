package com.librechat.android.core.common.result

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SafeApiCallTest {

    @Test
    fun `safeApiCall returns Success on successful block`() = runTest {
        val result = safeApiCall { "hello" }
        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data).isEqualTo("hello")
    }

    @Test
    fun `safeApiCall returns Error on exception`() = runTest {
        val result = safeApiCall<String> { throw IllegalStateException("boom") }
        assertThat(result).isInstanceOf(Result.Error::class.java)
        val error = result as Result.Error
        assertThat(error.message).isEqualTo("boom")
        assertThat(error.exception).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `isSuccess returns true for Success`() {
        val result: Result<String> = Result.Success("data")
        assertThat(result.isSuccess()).isTrue()
        assertThat(result.isError()).isFalse()
        assertThat(result.isLoading()).isFalse()
    }

    @Test
    fun `isError returns true for Error`() {
        val result: Result<String> = Result.Error(message = "fail")
        assertThat(result.isError()).isTrue()
        assertThat(result.isSuccess()).isFalse()
    }

    @Test
    fun `isLoading returns true for Loading`() {
        val result: Result<String> = Result.Loading
        assertThat(result.isLoading()).isTrue()
    }

    @Test
    fun `getOrNull returns data for Success`() {
        val result: Result<String> = Result.Success("data")
        assertThat(result.getOrNull()).isEqualTo("data")
    }

    @Test
    fun `getOrNull returns null for Error`() {
        val result: Result<String> = Result.Error(message = "fail")
        assertThat(result.getOrNull()).isNull()
    }

    @Test
    fun `getOrThrow returns data for Success`() {
        val result: Result<String> = Result.Success("data")
        assertThat(result.getOrThrow()).isEqualTo("data")
    }

    @Test
    fun `getOrThrow throws for Error with exception`() {
        val original = IllegalArgumentException("bad arg")
        val result: Result<String> = Result.Error(original, "bad arg")
        try {
            result.getOrThrow()
            assertThat(false).isTrue() // Should not reach here
        } catch (e: Exception) {
            assertThat(e).isSameInstanceAs(original)
        }
    }

    @Test
    fun `getOrThrow throws for Loading`() {
        val result: Result<String> = Result.Loading
        try {
            result.getOrThrow()
            assertThat(false).isTrue() // Should not reach here
        } catch (e: IllegalStateException) {
            assertThat(e.message).isEqualTo("Result is still loading")
        }
    }
}
