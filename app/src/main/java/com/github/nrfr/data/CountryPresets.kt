package com.github.nrfr.data

object CountryPresets {
    data class CountryInfo(
        val code: String,
        val name: String
    )

    private val PRIORITY_CODES = listOf("CN", "HK", "MO", "TW")

    private val ALL_COUNTRIES = listOf(
        CountryInfo("CN", "中国"),
        CountryInfo("HK", "中国香港"),
        CountryInfo("MO", "中国澳门"),
        CountryInfo("TW", "中国台湾"),
        CountryInfo("JP", "日本"),
        CountryInfo("KR", "韩国"),
        CountryInfo("US", "美国"),
        CountryInfo("GB", "英国"),
        CountryInfo("DE", "德国"),
        CountryInfo("FR", "法国"),
        CountryInfo("IT", "意大利"),
        CountryInfo("ES", "西班牙"),
        CountryInfo("PT", "葡萄牙"),
        CountryInfo("RU", "俄罗斯"),
        CountryInfo("IN", "印度"),
        CountryInfo("AU", "澳大利亚"),
        CountryInfo("NZ", "新西兰"),
        CountryInfo("SG", "新加坡"),
        CountryInfo("MY", "马来西亚"),
        CountryInfo("TH", "泰国"),
        CountryInfo("VN", "越南"),
        CountryInfo("ID", "印度尼西亚"),
        CountryInfo("PH", "菲律宾"),
        CountryInfo("CA", "加拿大"),
        CountryInfo("MX", "墨西哥"),
        CountryInfo("BR", "巴西"),
        CountryInfo("AR", "阿根廷"),
        CountryInfo("ZA", "南非")
    )

    /** 大中华区固定顺序，其余按中文名排序；运营商列表与此顺序一致。 */
    val countries: List<CountryInfo> = buildList {
        val byCode = ALL_COUNTRIES.associateBy { it.code }
        PRIORITY_CODES.mapNotNull { byCode[it] }.forEach { add(it) }
        ALL_COUNTRIES
            .filter { it.code !in PRIORITY_CODES }
            .sortedBy { it.name }
            .forEach { add(it) }
    }

    val countryOrder: Map<String, Int> = countries.withIndex().associate { (index, country) ->
        country.code to index
    }
} 