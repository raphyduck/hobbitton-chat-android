package com.garfiec.librechat.feature.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the settings form refuses to save.
 *
 * Every case here is one where saving would produce an error that names the wrong cause — the whole
 * point of validating at the form rather than letting the first request fail.
 */
class EngineSettingsValidationTest {

    private fun validate(
        baseUrl: String = "https://agent.example.com",
        issuerUrl: String = "https://auth.example.com",
        username: String = "opencode",
        typedPassword: String = "secret",
        passwordStored: Boolean = false,
        schedulerUrl: String = "",
    ) = validateEngineSettings(
        baseUrl, issuerUrl, username, typedPassword, passwordStored, schedulerUrl,
    )

    @Test
    fun `a complete form is accepted`() {
        assertTrue(validate().isEmpty())
    }

    @Test
    fun `a host without a scheme is refused`() {
        // Every text field accepts it and every HTTP client rejects it; the resulting « unknown
        // host » reads as a network outage on a phone whose network is fine.
        assertEquals(setOf(EngineSettingsField.BASE_URL), validate(baseUrl = "agent.example.com"))
    }

    @Test
    fun `a scheme with nothing after it is refused`() {
        assertEquals(setOf(EngineSettingsField.BASE_URL), validate(baseUrl = "https://"))
    }

    @Test
    fun `plain http is allowed towards a private host`() {
        // A laptop pointed at the engine on 127.0.0.1 has no certificate and needs none.
        assertTrue(validate(baseUrl = "http://127.0.0.1:4096").isEmpty())
        assertTrue(validate(baseUrl = "http://192.168.1.20:4096", issuerUrl = "http://auth.lan").isEmpty())
        assertTrue(validate(schedulerUrl = "http://[fd00::1]:8080").isEmpty())
    }

    @Test
    fun `plain http towards a public host is refused, field by field`() {
        // The Basic and the portal's bearer go on every request to these addresses (F5): in the
        // clear across the internet they are anyone's. Each address is judged on its own so the
        // form points at the one to fix.
        assertEquals(setOf(EngineSettingsField.BASE_URL), validate(baseUrl = "http://agent.example.com"))
        assertEquals(setOf(EngineSettingsField.ISSUER_URL), validate(issuerUrl = "http://auth.example.com"))
        assertEquals(
            setOf(EngineSettingsField.SCHEDULER_URL),
            validate(schedulerUrl = "http://203.0.113.10:8080"),
        )
    }

    @Test
    fun `a missing portal address is refused even though a request could be built`() {
        // The engine sits behind Authelia: without an issuer the first 302 fails renewal, with an
        // error naming neither the portal nor the missing setting.
        assertEquals(setOf(EngineSettingsField.ISSUER_URL), validate(issuerUrl = ""))
    }

    @Test
    fun `an empty password is refused when none has ever been saved`() {
        assertEquals(
            setOf(EngineSettingsField.PASSWORD),
            validate(typedPassword = "", passwordStored = false),
        )
    }

    @Test
    fun `an empty password is accepted when one is already saved`() {
        // Blank means « keep the stored one ». Refusing here would make someone retype a secret
        // every time they fix a typo in the URL.
        assertTrue(validate(typedPassword = "", passwordStored = true).isEmpty())
    }

    @Test
    fun `a blank username is refused`() {
        assertEquals(setOf(EngineSettingsField.USERNAME), validate(username = "   "))
    }

    @Test
    fun `an empty scheduler address is accepted`() {
        // Not having a scheduler is a normal state: the engine works without one, and the tab
        // simply shows no recurring missions. Refusing a blank field here would force everyone
        // to invent an address for a service they may not run.
        assertTrue(validate(schedulerUrl = "").isEmpty())
    }

    @Test
    fun `a scheduler address without a scheme is refused`() {
        // Optional does not mean unchecked: once it is filled in, it has to be reachable.
        assertEquals(
            setOf(EngineSettingsField.SCHEDULER_URL),
            validate(schedulerUrl = "sched.example.com"),
        )
    }

    @Test
    fun `every problem is reported at once`() {
        // One field at a time would send someone through four save-and-fix rounds.
        val problems = validate(
            baseUrl = "", issuerUrl = "", username = "", typedPassword = "",
            schedulerUrl = "sched.example.com",
        )

        assertEquals(EngineSettingsField.entries.toSet(), problems)
    }
}
