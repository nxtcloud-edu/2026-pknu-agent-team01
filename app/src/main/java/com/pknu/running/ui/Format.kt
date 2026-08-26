package com.pknu.running.ui

/** 러닝 지표 표시용 공통 포매터. */
object Format {

    /** 초 → "m:ss" */
    fun duration(totalSec: Long): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    /** sec/km → "m'ss\"/km" (없거나 비정상이면 --'--") */
    fun pace(secPerKm: Double?): String {
        if (secPerKm == null || secPerKm.isInfinite() || secPerKm.isNaN()) return "--'--\""
        val m = (secPerKm / 60).toInt()
        val s = (secPerKm % 60).toInt()
        return "%d'%02d\"/km".format(m, s)
    }
}
