package com.garfiec.librechat.core.network.engine.auth

/**
 * The app's client at the portal — what Authelia knows this application as
 * (`identity_providers.oidc.clients` server-side).
 *
 * One constant, and not a setting (D-076). It used to be written in three places — the settings
 * form's default, the settings store's default and the token client's wiring — and the form let
 * someone change the first without the others: the authorization URL then named one client while
 * the pushed request and the code exchange named another, a mismatch Authelia rejects with an
 * error that mentions neither. A deployment with another client id is a rebuild, not a form field.
 */
const val PORTAL_CLIENT_ID: String = "hobbitton-chat-android"
