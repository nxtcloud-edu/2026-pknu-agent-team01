package com.pknu.running.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 앱 전체가 공유하는 다크 테마 색상 팔레트.
 * 여러 화면(러닝/음악/캘린더)이 동일한 룩앤필을 유지하도록 한 곳에서 관리한다.
 */
object Palette {
    val BG = Color.parseColor("#0E1116")
    val SURFACE = Color.parseColor("#1A1F27")
    val SURFACE_2 = Color.parseColor("#2A313C")
    val ACCENT = Color.parseColor("#00E5A0")
    val ACCENT_DIM = Color.parseColor("#123A2E")
    val TEXT_MAIN = Color.parseColor("#F5F7FA")
    val TEXT_SUB = Color.parseColor("#8A94A6")

    // 러닝 상태 배지
    val STATE_READY = Color.parseColor("#4A5464")
    val STATE_RUNNING = Color.parseColor("#00B37E")
    val STATE_PAUSED = Color.parseColor("#E0A400")
    val STATE_FINISHED = Color.parseColor("#3B82F6")

    // 요일 색
    val SUNDAY = Color.parseColor("#FF6B6B")
    val SATURDAY = Color.parseColor("#5B9CFF")
}

/** dp → px */
fun Context.dp(v: Int): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
).toInt()

/** 채워진 둥근 배경 */
fun roundedBg(color: Int, radius: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
}

/** 테두리(stroke) 둥근 배경 */
fun Context.strokeBg(strokeColor: Int, radius: Float, fill: Int = Palette.SURFACE) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = radius
    setStroke(dp(1), strokeColor)
}

/** 알약(pill) 배경 */
fun pill(color: Int, radius: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
}

/** 원형 점 */
fun dot(color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
}

// ---------------------------------------------------------------- 공통 뷰 팩토리

fun Context.verticalSpacer(size: Int) = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(1, size)
}

fun Context.horizontalSpacer(size: Int) = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(size, 1)
}

/** 큰 화면 제목 */
fun Context.screenTitle(text: String) = TextView(this).apply {
    this.text = text
    setTextColor(Palette.TEXT_MAIN)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
    setTypeface(typeface, Typeface.BOLD)
    letterSpacing = -0.01f
}

/** 섹션 라벨 */
fun Context.sectionLabel(text: String) = TextView(this).apply {
    this.text = text
    setTextColor(Palette.TEXT_MAIN)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    setTypeface(typeface, Typeface.BOLD)
}

/** 둥근 카드 컨테이너 */
fun Context.roundedCard(child: View, bg: Int = Palette.SURFACE, radius: Int = 18): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = roundedBg(bg, dp(radius).toFloat())
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(child)
    }

/** 강조 버튼 (채워진 색) */
fun Context.primaryButton(
    text: String,
    bg: Int,
    textColor: Int = Color.WHITE,
    onClick: () -> Unit,
) = TextView(this).apply {
    this.text = text
    gravity = Gravity.CENTER
    setTextColor(textColor)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    setTypeface(typeface, Typeface.BOLD)
    background = roundedBg(bg, dp(16).toFloat())
    isClickable = true
    isFocusable = true
    setOnClickListener { onClick() }
}

/** 보조 버튼 (테두리) */
fun Context.secondaryButton(text: String, onClick: () -> Unit) = TextView(this).apply {
    this.text = text
    gravity = Gravity.CENTER
    setTextColor(Palette.TEXT_MAIN)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    background = strokeBg(Palette.SURFACE_2, dp(14).toFloat())
    isClickable = true
    isFocusable = true
    setOnClickListener { onClick() }
}

/** 버튼들을 가로로 균등 배치 */
fun Context.buttonRow(heightDp: Int, vararg views: View) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    views.forEachIndexed { i, v ->
        if (i > 0) addView(horizontalSpacer(dp(10)))
        v.layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), 1f)
        addView(v)
    }
}
