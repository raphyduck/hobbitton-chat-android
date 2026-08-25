package com.garfiec.librechat.core.network.engine.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Ce que le système délivre sur `at.hobbitton.chat://oauth`.
 *
 * Ce point d'entrée est ouvert à toute application de l'appareil — c'est la nature d'un schéma
 * applicatif — donc ce qu'il refuse compte autant que ce qu'il accepte. Ce qui garde le code, ce
 * n'est pas l'exclusivité du schéma : c'est PKCE, et le `state` vérifié avant que le code ne soit
 * dépensé.
 */
class ParseCallbackUriTest {

    @Test
    fun `un retour de portail rend le code et le state`() {
        val result = parseCallbackUri("at.hobbitton.chat://oauth?code=abc&state=xyz")

        assertThat(result).isEqualTo(FormPostCallback.Success(code = "abc", state = "xyz"))
    }

    @Test
    fun `les valeurs sont decodees`() {
        // La page du serveur encode ce qu'Authelia lui a POSTé. Un `state` contenant `/` ou `+`
        // ressortirait faux sans décodage, et serait rejeté au contrôle pour rien.
        val result = parseCallbackUri("at.hobbitton.chat://oauth?code=a%2Fb&state=x%2By")

        assertThat(result).isEqualTo(FormPostCallback.Success(code = "a/b", state = "x+y"))
    }

    @Test
    fun `un autre schema n'est pas le retour du portail`() {
        val result = parseCallbackUri("librechat://conversation/42?code=abc&state=xyz")

        assertThat(result).isInstanceOf(FormPostCallback.Malformed::class.java)
    }

    @Test
    fun `un autre hote sur le meme schema est refuse`() {
        // `at.hobbitton.chat://autre-chose` viendrait d'un autre usage du schéma, pas du portail.
        val result = parseCallbackUri("at.hobbitton.chat://reglages?code=abc&state=xyz")

        assertThat(result).isInstanceOf(FormPostCallback.Malformed::class.java)
    }

    @Test
    fun `la casse du schema et de l'hote n'a pas d'importance`() {
        // Le système normalise en minuscules ; rien n'oblige la page du serveur à en faire autant.
        val result = parseCallbackUri("AT.Hobbitton.Chat://OAuth?code=abc&state=xyz")

        assertThat(result).isEqualTo(FormPostCallback.Success(code = "abc", state = "xyz"))
    }

    @Test
    fun `une barre finale ne change rien`() {
        val result = parseCallbackUri("at.hobbitton.chat://oauth/?code=abc&state=xyz")

        assertThat(result).isEqualTo(FormPostCallback.Success(code = "abc", state = "xyz"))
    }

    @Test
    fun `le fragment est jete avant la requete`() {
        // Sans ça, `?code=abc#zz` donnerait un code de « abc#zz » — refusé à l'échange avec un
        // `invalid_grant` qui se lit comme un code expiré, et n'en est pas un.
        val result = parseCallbackUri("at.hobbitton.chat://oauth?code=abc&state=xyz#quelquechose")

        assertThat(result).isEqualTo(FormPostCallback.Success(code = "abc", state = "xyz"))
    }

    @Test
    fun `un refus est rapporte comme tel, avec sa raison`() {
        val result = parseCallbackUri(
            "at.hobbitton.chat://oauth?error=access_denied&error_description=User%20refused&state=xyz",
        )

        assertThat(result).isEqualTo(
            FormPostCallback.Failure(
                error = "access_denied",
                description = "User refused",
                state = "xyz",
            ),
        )
    }

    @Test
    fun `un refus sans state reste un refus`() {
        // Un portail qui refuse avant d'avoir lu la demande n'a pas de `state` à rendre. L'exiger
        // transformerait un refus lisible en « lien malformé », et masquerait la vraie cause.
        val result = parseCallbackUri("at.hobbitton.chat://oauth?error=invalid_request")

        assertThat(result).isEqualTo(
            FormPostCallback.Failure(error = "invalid_request", description = null, state = null),
        )
    }

    @Test
    fun `un code sans state est malforme, pas un succes`() {
        // Le `state` est la défense de ce flux contre une réponse injectée. Un code sans lui n'est
        // pas utilisable, et l'accepter reviendrait à supprimer le contrôle.
        val result = parseCallbackUri("at.hobbitton.chat://oauth?code=abc")

        assertThat(result).isInstanceOf(FormPostCallback.Malformed::class.java)
    }

    @Test
    fun `un lien sans parametre du tout est malforme`() {
        assertThat(parseCallbackUri("at.hobbitton.chat://oauth"))
            .isInstanceOf(FormPostCallback.Malformed::class.java)
    }

    @Test
    fun `un lien demesure est refuse plutot qu'analyse`() {
        val result = parseCallbackUri("at.hobbitton.chat://oauth?code=" + "a".repeat(100_000))

        assertThat(result).isInstanceOf(FormPostCallback.Malformed::class.java)
    }
}
