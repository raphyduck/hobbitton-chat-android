package com.garfiec.librechat.core.common

/** The app's cache directories other code relies on by name. */
object AppCache {
    /** Opened attachments and the diagnostic log; the FileProvider serves only this (app/src/main/res/xml/attachment_paths.xml says the same). */
    const val ATTACHMENTS_DIR = "attachments"
}
