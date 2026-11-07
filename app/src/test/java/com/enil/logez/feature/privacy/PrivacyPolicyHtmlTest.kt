package com.enil.logez.feature.privacy

import com.enil.logez.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PrivacyPolicyHtmlTest {
    /**
     * Health Connect requires the hosted policy to be the one users see in the app. The in-app
     * screen renders [PrivacyPolicyContent] directly, so the only way the two can differ is a
     * stale `site/privacy/index.html`. This fails as soon as it is, and leaves the page it expected
     * under app/build/ ready to copy over.
     */
    @Test
    fun `the hosted policy page matches the in-app policy`() {
        val expected = PrivacyPolicyHtml.render(PrivacyPolicyContent.document(BuildConfig.CONTACT_EMAIL))
        val site = File("../site/privacy/index.html")
        if (!site.isFile || site.readText() != expected) {
            val regenerated = File("build/privacy-policy/index.html").apply {
                parentFile!!.mkdirs()
                writeText(expected)
            }
            fail(
                "site/privacy/index.html is out of date with PrivacyPolicyContent (or logez.contactEmail changed). " +
                    "Copy ${regenerated.absolutePath} over it.",
            )
        }
    }

    @Test
    fun `an unset contact email shows the placeholder rather than an empty sentence`() {
        val text = PrivacyPolicyHtml.render(PrivacyPolicyContent.document(""))
        assertTrue(text.contains(PrivacyPolicyHtml.escape(PrivacyPolicyContent.CONTACT_PLACEHOLDER)))
    }

    @Test
    fun `a configured email becomes a mailto link and URLs become links`() {
        assertEquals(
            "Mail <a href=\"mailto:dev@example.com\">dev@example.com</a>.",
            PrivacyPolicyHtml.linkify("Mail dev@example.com."),
        )
        assertEquals(
            "See <a href=\"https://openfreemap.org/privacy/\">https://openfreemap.org/privacy/</a>",
            PrivacyPolicyHtml.linkify("See https://openfreemap.org/privacy/"),
        )
    }

    @Test
    fun `text is escaped so policy wording can never inject markup`() {
        assertEquals("a &lt;b&gt; &amp; &quot;c&quot;", PrivacyPolicyHtml.escape("a <b> & \"c\""))
    }

    /** The claims the audit found false must not come back. */
    @Test
    fun `the policy no longer claims that nothing is collected or sent`() {
        val text = PrivacyPolicyHtml.render(PrivacyPolicyContent.document("dev@example.com")).lowercase()
        assertFalse(text.contains("does not collect any data"))
        assertFalse(text.contains("nothing is sent anywhere"))
        assertFalse(text.contains("maptiler"))
        assertTrue(text.contains("openfreemap"))
    }
}
