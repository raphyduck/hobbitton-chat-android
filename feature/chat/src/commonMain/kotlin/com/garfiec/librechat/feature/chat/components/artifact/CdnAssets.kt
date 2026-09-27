package com.garfiec.librechat.feature.chat.components.artifact

/**
 * A script or stylesheet the WebView templates load from a CDN, pinned to one exact file.
 *
 * Every entry names a versioned URL and the Subresource Integrity hash of the bytes that URL
 * served on 26/09/2026 (`curl` from cdn.jsdelivr.net, `openssl dgst -sha384 -binary | base64`).
 * Unpinned URLs (`mermaid@10`, `highlight.js@11`, `marked`, `@babel/standalone`) let a CDN
 * publish, or a CDN compromise, run new code inside the app's WebViews with no change on our
 * side (review C8). With `integrity` + `crossorigin="anonymous"` the browser refuses bytes that
 * do not hash to the value recorded here, and a bump is a deliberate edit of this file.
 */
internal class CdnAsset(val url: String, val integrity: String) {

    fun scriptTag(): String = """<script src="$url" integrity="$integrity" crossorigin="anonymous"></script>"""

    fun stylesheetTag(): String =
        """<link rel="stylesheet" href="$url" integrity="$integrity" crossorigin="anonymous">"""
}

internal object CdnAssets {

    const val JSDELIVR_HOST = "cdn.jsdelivr.net"
    const val ESM_HOST = "esm.sh"
    const val TAILWIND_HOST = "cdn.tailwindcss.com"

    /** mermaid 10.9.8 — the last UMD line; v11 ships ESM only, which `loadDataWithBaseURL` cannot run. */
    val MERMAID = CdnAsset(
        url = "https://cdn.jsdelivr.net/npm/mermaid@10.9.8/dist/mermaid.min.js",
        integrity = "sha384-N3QqR/7q+xm3BGX+CBbNI8AUmRRqcsDzToy+0z1NLDI0QmTKW8zvwLvqulJgk3dP",
    )

    val MARKED = CdnAsset(
        url = "https://cdn.jsdelivr.net/npm/marked@18.0.14/lib/marked.umd.min.js",
        integrity = "sha384-1KNqLSVIIDocc7NKjWP/vfNnoRSAenAfiLA3OnW7YOebcl46U/07fZMCkfzuBa+a",
    )

    val DOMPURIFY = CdnAsset(
        url = "https://cdn.jsdelivr.net/npm/dompurify@3.4.16/dist/purify.min.js",
        integrity = "sha384-a7SzOxErzJ3ZpQz0zJ32d67dSitNzPcbfybc/ykU9KJhMgZkwqfSxlhhdJRS+XGL",
    )

    val HLJS_CORE = CdnAsset(
        url = "https://cdn.jsdelivr.net/npm/highlight.js@11.12.0/lib/core.min.js",
        integrity = "sha384-OABGn5Qtg5WejkcGDOekHh2AElFB6dXFWEa31xhv9dLEYwlEXhdeilGlT+uGkxsT",
    )

    val HLJS_COMMON = CdnAsset(
        url = "https://cdn.jsdelivr.net/npm/highlight.js@11.12.0/lib/common.min.js",
        integrity = "sha384-tZMGR9g/AD5nc6tg27TzR7XBlNL6Xi53gmi7N01A8s4VBgK4w2snnsxJfjIzn/rK",
    )

    val HLJS_STYLE_LIGHT = CdnAsset(
        url = "https://cdn.jsdelivr.net/npm/highlight.js@11.12.0/styles/github.min.css",
        integrity = "sha384-eFTL69TLRZTkNfYZOLM+G04821K1qZao/4QLJbet1pP4tcF+fdXq/9CdqAbWRl/L",
    )

    val HLJS_STYLE_DARK = CdnAsset(
        url = "https://cdn.jsdelivr.net/npm/highlight.js@11.12.0/styles/github-dark.min.css",
        integrity = "sha384-wH75j6z1lH97ZOpMOInqhgKzFkAInZPPSPlZpYKYTOqsaizPvhQZmAtLcPKXpLyH",
    )

    val KATEX_SCRIPT = CdnAsset(
        url = "https://cdn.jsdelivr.net/npm/katex@0.16.21/dist/katex.min.js",
        integrity = "sha384-Rma6DA2IPUwhNxmrB/7S3Tno0YY7sFu9WSYMCuulLhIqYSGZ2gKCJWIqhBWqMQfh",
    )

    val KATEX_STYLE = CdnAsset(
        url = "https://cdn.jsdelivr.net/npm/katex@0.16.21/dist/katex.min.css",
        integrity = "sha384-zh0CIslj+VczCZtlzBcjt5ppRcsAmDnRem7ESsYwWwg3m/OaJ2l4x7YBZl9Kxxib",
    )

    /** Moved from unpkg.com (unversioned) to jsdelivr so the React runner has one pinned CDN. */
    val BABEL_STANDALONE = CdnAsset(
        url = "https://cdn.jsdelivr.net/npm/@babel/standalone@7.29.9/babel.min.js",
        integrity = "sha384-oLbIC13I/8DNBviftYPfOFQS5DC2WUmwk0SyIPmnzu1Ui+vYyATSSBJiJ5ETa4o/",
    )

    /**
     * Tailwind's Play CDN, pinned to 3.4.17 (the v3 class vocabulary artifacts are written in).
     * **No `integrity` attribute**: cdn.tailwindcss.com serves no `Access-Control-Allow-Origin`
     * header (checked 26/09/2026), and SRI can only verify a CORS-enabled fetch — the browser would
     * refuse the script outright. The exact version is the guarantee that remains.
     */
    const val TAILWIND_SCRIPT_TAG = """<script src="https://cdn.tailwindcss.com/3.4.17"></script>"""
}
