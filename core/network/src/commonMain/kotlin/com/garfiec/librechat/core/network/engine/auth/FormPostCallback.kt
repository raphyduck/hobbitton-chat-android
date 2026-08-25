package com.garfiec.librechat.core.network.engine.auth

import io.ktor.http.parseUrlEncodedParameters

/**
 * Ce que le portail a fini par rapporter à l'application.
 *
 * Le nom garde « FormPost » parce que c'est bien un `form_post` qu'Authelia émet — il n'a juste
 * plus l'application pour destinataire. Le POST atterrit sur
 * `https://<planificateur>/oauth/authelia`, et le serveur rend une page qui rebondit vers
 * `at.hobbitton.chat://oauth?code=…&state=…` en GET. C'est cette URL-là qui arrive ici.
 *
 * ## Pourquoi ce détour, plutôt qu'un socket local
 *
 * Vérifié contre `authelia validate-config` : le scope `authelia.bearer.authz` impose
 * `response_mode=form_post`, et un POST de formulaire ne peut pas être délivré à un schéma
 * d'application — une App Link perd le corps. La première réponse a donc été une socket sur
 * `127.0.0.1` (RFC 8252 §7.3), et elle a échoué sur l'appareil le 24/08 : le navigateur a reçu
 * « connection timed out ». Pas *refused*, qui aurait dit « port fermé » — un délai, qui dit que le
 * noyau avait accepté la connexion et que **personne côté application n'est venu la chercher**.
 * Android met en cache puis gèle une application passée en arrière-plan, et son `accept()` ne
 * s'exécute plus.
 *
 * Un lien profond n'a pas ce problème : il **réveille** l'application au lieu de supposer qu'elle
 * tourne encore. Voir D-049 côté serveur.
 *
 * ## Ce que le schéma applicatif ne garantit pas
 *
 * N'importe quelle application installée peut le réclamer. C'est la limite connue du motif, et
 * c'est exactement ce que PKCE et `state` couvrent : un code intercepté ne s'échange pas sans le
 * `code_verifier` qui n'a jamais quitté cette application, et un `state` étranger est rejeté avant
 * que le code ne soit dépensé.
 */
sealed interface FormPostCallback {
    data class Success(val code: String, val state: String) : FormPostCallback

    /** Le portail a dit non — demande expirée, consentement refusé, second facteur abandonné. */
    data class Failure(val error: String, val description: String?, val state: String?) :
        FormPostCallback

    /** Quelque chose est arrivé qui n'était pas le retour du portail. */
    data class Malformed(val reason: String) : FormPostCallback
}

/** Le schéma que le manifeste déclare, et vers lequel la page du serveur rebondit. */
const val CALLBACK_SCHEME: String = "at.hobbitton.chat"

/** L'hôte du lien profond. `at.hobbitton.chat://oauth`. */
const val CALLBACK_HOST: String = "oauth"

private const val CALLBACK_PREFIX = "$CALLBACK_SCHEME://$CALLBACK_HOST"

/**
 * Une URL de lien profond n'a aucune raison d'être longue. Un plafond parce que ce point d'entrée
 * est ouvert à toute application de l'appareil, et qu'un refus vaut mieux qu'un tampon sans borne.
 */
private const val MAX_URI_LENGTH = 8 * 1024

/**
 * Lit ce que le système a délivré sur `at.hobbitton.chat://oauth`.
 *
 * Analysé à la main plutôt qu'avec `Url` de Ktor : ce schéma n'est pas HTTP, et un analyseur d'URL
 * générique a ses propres idées sur ce qu'est un hôte, un port et une normalisation. Ce qu'il faut
 * ici est étroit et vérifiable — préfixe attendu, fragment jeté, requête décodée — et le faire
 * explicitement évite de dépendre du comportement d'une bibliothèque sur un cas qu'elle ne
 * documente pas.
 *
 * Trois points valent d'être nommés :
 *
 *  * **le fragment part avant la requête**, sinon `?code=x#y` ferait un code de `x#y` ;
 *  * **la comparaison de préfixe est insensible à la casse**, parce que le système normalise le
 *    schéma et l'hôte en minuscules, mais que rien n'oblige la page du serveur à faire pareil ;
 *  * **`state` est exigé pour un succès, seulement rapporté pour un refus** : un portail qui
 *    refuse avant d'avoir lu la demande n'a pas de `state` à rendre, et l'exiger là transformerait
 *    un refus lisible en « lien malformé ».
 */
fun parseCallbackUri(uri: String): FormPostCallback {
    if (uri.length > MAX_URI_LENGTH) return FormPostCallback.Malformed("callback too large")

    val withoutFragment = uri.substringBefore('#')
    val path = withoutFragment.substringBefore('?').trimEnd('/')
    if (!path.equals(CALLBACK_PREFIX, ignoreCase = true)) {
        return FormPostCallback.Malformed("not the callback")
    }

    val fields = withoutFragment.substringAfter('?', missingDelimiterValue = "")
        .parseUrlEncodedParameters()

    fields["error"]?.let { error ->
        return FormPostCallback.Failure(
            error = error,
            description = fields["error_description"],
            state = fields["state"],
        )
    }

    val code = fields["code"] ?: return FormPostCallback.Malformed("no code in callback")
    val state = fields["state"] ?: return FormPostCallback.Malformed("no state in callback")
    return FormPostCallback.Success(code = code, state = state)
}
