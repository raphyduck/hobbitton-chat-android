package com.garfiec.librechat.core.data

import java.security.MessageDigest

/** What [MessageDigest.digest] returns, in lowercase hex. */
internal fun MessageDigest.hexDigest(): String = digest().joinToString("") { "%02x".format(it) }
