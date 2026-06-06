package com.geoagent.agent

import kotlin.math.roundToInt

/**
 * Local agent that performs geological unit conversions.
 * Zero network dependency — pure Kotlin computation.
 */
class UnitConversionAgent {

    companion object {
        val META = AgentMeta(
            name = "unit_conversion",
            displayName = "单位换算",
            description = "地质学常用单位换算（深度、压力、温度、角度）",
            keywords = setOf(
                "换算", "转换", "单位", "等于多少", "几米", "几英尺",
                "米", "英尺", "千米", "公里", "英里", "厘米", "毫米",
                "帕斯卡", "兆帕", "千帕", "巴", "psi",
                "温度", "摄氏", "华氏", "开尔文", "多少度",
                "角度", "弧度"
            ),
            regexPatterns = listOf(
                Regex("""\d+(\.\d+)?\s*(米|m|英尺|ft|千米|km|毫米|mm|厘米|cm|英里|mi)\s*(等于|换算|转换|转成|是多少|to)\s*(米|m|英尺|ft|千米|km|毫米|mm|厘米|cm|英里|mi)""", RegexOption.IGNORE_CASE),
                Regex("""\d+(\.\d+)?\s*(摄氏|华氏|开尔文|°c|°f|k)\s*(等于|换算|转换|转成|是多少|to)\s*(摄氏|华氏|开尔文|°c|°f|k)""", RegexOption.IGNORE_CASE),
                Regex("""\d+(\.\d+)?\s*(兆帕|千帕|帕斯卡|psi|mpa|kpa|pa|bar)\s*(等于|换算|转换|转成|是多少|to)\s*(兆帕|千帕|帕斯卡|psi|mpa|kpa|pa|bar)""", RegexOption.IGNORE_CASE),
                Regex("""(换算|转换|convert|转成).*(米|m|英尺|ft|千米|km|毫米|mm|厘米|cm|英里|mi|兆帕|千帕|帕斯卡|psi|摄氏|华氏|开尔文|弧度|度)""", RegexOption.IGNORE_CASE)
            ),
            semanticHints = setOf("换算", "转换", "等于多少", "多少", "convert"),
            priority = 10,
            requiresAuth = false,
            ttlMinutes = 5
        )
    }

    // ---- unit aliases & conversion tables ----

    private data class UnitDef(val canonical: String, val aliases: List<String>, val category: String)

    private val units = listOf(
        // Length / Depth
        UnitDef("m",  listOf("m", "米", "meter", "metre"), "length"),
        UnitDef("km", listOf("km", "千米", "公里", "kilometer"), "length"),
        UnitDef("cm", listOf("cm", "厘米", "centimeter"), "length"),
        UnitDef("mm", listOf("mm", "毫米", "millimeter"), "length"),
        UnitDef("ft", listOf("ft", "英尺", "foot", "feet"), "length"),
        UnitDef("mi", listOf("mi", "英里", "mile"), "length"),
        // Pressure
        UnitDef("Pa",   listOf("pa", "帕", "帕斯卡", "pascal"), "pressure"),
        UnitDef("kPa",  listOf("kpa", "千帕"), "pressure"),
        UnitDef("MPa",  listOf("mpa", "兆帕"), "pressure"),
        UnitDef("bar",  listOf("bar", "巴"), "pressure"),
        UnitDef("psi",  listOf("psi", "磅/平方英寸", "磅力/平方英寸"), "pressure"),
        // Temperature
        UnitDef("C", listOf("c", "°c", "摄氏度", "摄氏", "celsius"), "temperature"),
        UnitDef("F", listOf("f", "°f", "华氏度", "华氏", "fahrenheit"), "temperature"),
        UnitDef("K", listOf("k", "开尔文", "开", "kelvin"), "temperature"),
        // Angle
        UnitDef("deg", listOf("deg", "°", "度", "degree", "degrees"), "angle"),
        UnitDef("rad", listOf("rad", "弧度", "radian", "radians"), "angle"),
    )

    /** Conversion ratio: 1 canonical → how many of this unit */
    private fun canonicalToUnit(canonical: String): Double = when (canonical) {
        "m" -> 1.0
        "km" -> 0.001
        "cm" -> 100.0
        "mm" -> 1000.0
        "ft" -> 3.28084
        "mi" -> 0.000621371
        "Pa" -> 1.0
        "kPa" -> 0.001
        "MPa" -> 1e-6
        "bar" -> 1e-5
        "psi" -> 0.000145038
        "deg" -> 1.0
        "rad" -> Math.PI / 180.0
        else -> 1.0
    }

    // ---- public API ----

    fun parse(input: String): ConversionRequest? {
        val cleaned = input
            .replace(Regex("^(请帮我|帮我|麻烦|请)\\s*"), "")
            .replace(",", ".")
            .trim()
        if (cleaned.isBlank()) return null

        for (fromUnit in units) {
            for (toUnit in units) {
                if (fromUnit.canonical == toUnit.canonical) continue
                if (fromUnit.category != toUnit.category) continue

                // Try patterns: "3000米 英尺", "3000 米 英尺", "3000米等于多少英尺"
                for (fromAlias in fromUnit.aliases.filter { it.length >= 2 }) {
                    for (toAlias in toUnit.aliases.filter { it.length >= 2 }) {
                        // Pattern: "3000米 英尺"
                        val regex = Regex(
                            """^(\d+\.?\d*)\s*${Regex.escape(fromAlias)}\s+${Regex.escape(toAlias)}\s*${'$'}""",
                            RegexOption.IGNORE_CASE
                        )
                        val match = regex.find(cleaned)
                        if (match != null) {
                            val value = match.groupValues[1].toDoubleOrNull() ?: continue
                            return ConversionRequest(value, fromUnit.canonical, toUnit.canonical)
                        }

                        // Pattern: "3000米等于多少英尺"
                        val regex2 = Regex("""^(\d+\.?\d*)\s*${Regex.escape(fromAlias)}\s*[=＝]?\s*(等于多少|等于|转|转成|转换|换|换算|是多少|to|convert)\s*${Regex.escape(toAlias)}\s*${'$'}""", RegexOption.IGNORE_CASE)
                        val match2 = regex2.find(cleaned)
                        if (match2 != null) {
                            val value = match2.groupValues[1].toDoubleOrNull() ?: continue
                            return ConversionRequest(value, fromUnit.canonical, toUnit.canonical)
                        }

                        val regex3 = Regex("""^(把)?\s*(\d+\.?\d*)\s*${Regex.escape(fromAlias)}\s*(换算|转换|转成|变成)\s*(成)?\s*${Regex.escape(toAlias)}\s*${'$'}""", RegexOption.IGNORE_CASE)
                        val match3 = regex3.find(cleaned)
                        if (match3 != null) {
                            val value = match3.groupValues[2].toDoubleOrNull() ?: continue
                            return ConversionRequest(value, fromUnit.canonical, toUnit.canonical)
                        }
                    }
                }
            }
        }
        return null
    }

    fun convert(request: ConversionRequest): ConversionResult {
        val fromCanonical = request.fromUnit
        val toCanonical = request.toUnit

        // Find unit labels
        val fromDef = units.find { it.canonical == fromCanonical }
        val toDef = units.find { it.canonical == toCanonical }
        val fromLabel = fromDef?.aliases?.firstOrNull() ?: fromCanonical
        val toLabel = toDef?.aliases?.firstOrNull() ?: toCanonical
        val category = fromDef?.category

        val result = when (category) {
            "temperature" -> convertTemperature(request.value, fromCanonical, toCanonical, fromLabel, toLabel)
            else -> convertRatio(request.value, fromCanonical, toCanonical, fromLabel, toLabel)
        }
        return result
    }

    private fun convertRatio(
        value: Double, from: String, to: String,
        fromLabel: String, toLabel: String
    ): ConversionResult {
        // value * (canonicalPerFrom) → canonical → (toPerCanonical) → target
        val asCanonical = value / canonicalToUnit(from)
        val asTarget = asCanonical * canonicalToUnit(to)
        val ratio = canonicalToUnit(to) / canonicalToUnit(from)
        return ConversionResult(
            input = "${formatNum(value)}$fromLabel",
            output = "${formatNum(asTarget)}$toLabel",
            formula = "1${fromLabel} = ${formatNum(ratio)}${toLabel}"
        )
    }

    private fun convertTemperature(
        value: Double, from: String, to: String,
        fromLabel: String, toLabel: String
    ): ConversionResult {
        // Convert to Celsius first
        val celsius = when (from) {
            "F" -> (value - 32) * 5.0 / 9.0
            "K" -> value - 273.15
            else -> value // already C
        }
        // Convert from Celsius to target
        val target = when (to) {
            "F" -> celsius * 9.0 / 5.0 + 32
            "K" -> celsius + 273.15
            else -> celsius // to C
        }
        return ConversionResult(
            input = "${formatNum(value)}°${fromLabel}",
            output = "${formatNum(target)}°${toLabel}",
            formula = when {
                from == "C" && to == "F" -> "°F = °C × 9/5 + 32"
                from == "F" && to == "C" -> "°C = (°F - 32) × 5/9"
                from == "C" && to == "K" -> "K = °C + 273.15"
                from == "K" && to == "C" -> "°C = K - 273.15"
                else -> "${fromLabel} → ${toLabel}"
            }
        )
    }

    private fun formatNum(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        return if (v == v.roundToInt().toDouble() && v < 1e6) {
            v.roundToInt().toString()
        } else {
            "%.4f".format(v).trimEnd('0').trimEnd('.')
        }
    }
}

data class ConversionRequest(
    val value: Double,
    val fromUnit: String,
    val toUnit: String
)

data class ConversionResult(
    val input: String,
    val output: String,
    val formula: String
)