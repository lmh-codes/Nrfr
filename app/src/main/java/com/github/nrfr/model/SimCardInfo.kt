package com.github.nrfr.model

data class SimCardInfo(
    val slot: Int,
    val subId: Int,
    val carrierName: String,
    val currentConfig: Map<String, String> = emptyMap()
) 