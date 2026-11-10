package com.garfiec.librechat.core.data.engine

import com.garfiec.librechat.core.model.engine.EnginePermissionRule
import com.garfiec.librechat.core.model.scheduler.ConnectorCatalogue

/**
 * A ruleset as this app wrote it until 10/11/2026, when the scheduler took over writing the rules
 * (Raphaël: the server defines the modes). The conversations opened before still carry such rules,
 * and the screen reads their connectors back from them ([connectorsGranted]) until the first send
 * has the scheduler rewrite them: a fixture for that reading, no longer the app's code.
 */
internal fun legacyRules(catalogue: ConnectorCatalogue, connectors: List<String>): List<EnginePermissionRule> {
    val granted = connectors.mapNotNull { catalogue.connecteurs[it] }.filter { it.direct }.flatMap { it.outils }.toSet()
    val rules = mutableListOf(EnginePermissionRule(permission = "task", action = "deny"))
    catalogue.connecteurs.values.filter { it.direct }.flatMap { it.outils }.distinct()
        .filter { it !in granted && it !in catalogue.socle }
        .forEach { rules += EnginePermissionRule(permission = it, action = "deny") }
    catalogue.socle.forEach { (tool, action) -> rules += EnginePermissionRule(permission = tool, action = action) }
    rules += EnginePermissionRule(permission = "question", action = "allow")
    granted.forEach { rules += EnginePermissionRule(permission = it, action = "allow") }
    return rules
}
