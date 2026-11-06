package com.tcc.devicehealth.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StringResourcesTest {
    @Test
    fun portugueseTranslationCoversEveryEnglishString() {
        val english = resourceNames("src/main/res/values/strings.xml")
        val portuguese = resourceNames("src/main/res/values-pt-rBR/strings.xml")

        assertEquals(emptySet<String>(), english - portuguese)
        assertEquals(emptySet<String>(), portuguese - english)
    }

    private fun resourceNames(path: String): Set<String> =
        Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(File(path).readText()).map { it.groupValues[1] }.toSet()
}
