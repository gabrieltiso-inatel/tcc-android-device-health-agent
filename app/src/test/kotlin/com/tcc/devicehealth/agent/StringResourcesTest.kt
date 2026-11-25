package com.tcc.devicehealth.agent

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

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
