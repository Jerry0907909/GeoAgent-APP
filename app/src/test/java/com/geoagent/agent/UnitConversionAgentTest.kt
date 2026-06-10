package com.geoagent.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConversionAgentTest {
    private val agent = UnitConversionAgent()

    @Test
    fun parseNaturalLanguageChineseConversion() {
        val req = agent.parse("3000米等于多少英尺")
        assertNotNull(req)
        assertEquals("m", req?.fromUnit)
        assertEquals("ft", req?.toUnit)
    }

    @Test
    fun parseColloquialSentenceConversion() {
        val req = agent.parse("把100摄氏度转成华氏度")
        assertNotNull(req)
        assertEquals("C", req?.fromUnit)
        assertEquals("F", req?.toUnit)
    }

    @Test
    fun convertLengthIsAccurateEnough() {
        val req = ConversionRequest(value = 3000.0, fromUnit = "m", toUnit = "ft")
        val result = agent.convert(req)

        assertTrue(result.output.contains("9842.52"))
    }
}
