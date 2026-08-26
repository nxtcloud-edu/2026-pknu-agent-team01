package com.pknu.running.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pknu.running.demo.DemoViewModel
import com.pknu.running.tracking.location.FusedLocationProvider
import com.pknu.running.tracking.model.RunningMetrics
import com.pknu.running.tracking.model.RunningState
import com.pknu.running.tracking.model.RunningTarget
import kotlinx.coroutines.launch

/**
 * 러닝 기록·상태 인식 화면. (기존 DemoActivity UI를 Fragment로 이전)
 */
class RunningFragment : Fragment() {

    private val vm: DemoViewModel by viewModels()

    private lateinit var stateBadge: TextView
    private lateinit var distanceValue: TextView
    private lateinit var timeValue: TextView
    private lateinit var currentPaceValue: TextView
    private lateinit var avgPaceValue: TextView
    private lateinit var smoothPaceValue: TextView
    private lateinit var gpsValue: TextView
    private lateinit var eventView: TextView

    private lateinit var startFakeBtn: TextView
    private lateinit var startGpsBtn: TextView
    private lateinit var pauseBtn: TextView
    private lateinit var resumeBtn: TextView
    private lateinit var finishBtn: TextView

    private var summaryDialog: AlertDialog? = null

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRealRun() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = buildUi()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        observe()
        applyButtonState(RunningState.READY)
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(20), ctx.dp(24), ctx.dp(20), ctx.dp(24))
        }

        root.addView(buildHeader())
        root.addView(ctx.verticalSpacer(ctx.dp(22)))

        root.addView(buildHeroCard())
        root.addView(ctx.verticalSpacer(ctx.dp(12)))

        root.addView(buildStatsRow())
        root.addView(ctx.verticalSpacer(ctx.dp(10)))
        root.addView(buildSubStatsRow())
        root.addView(ctx.verticalSpacer(ctx.dp(24)))

        startFakeBtn = ctx.primaryButton("▶  Fake 러닝", Palette.ACCENT) { vm.startFakeRun() }
        startGpsBtn = ctx.primaryButton("＋  실제 GPS", Palette.SURFACE_2, Palette.TEXT_MAIN) { onRealClicked() }
        root.addView(ctx.buttonRow(54, startFakeBtn, startGpsBtn))
        root.addView(ctx.verticalSpacer(ctx.dp(10)))

        pauseBtn = ctx.secondaryButton("Ⅱ 일시정지") { vm.pause() }
        resumeBtn = ctx.secondaryButton("▶ 재개") { vm.resume() }
        finishBtn = ctx.secondaryButton("■ 종료") { vm.finish() }
        root.addView(ctx.buttonRow(54, pauseBtn, resumeBtn, finishBtn))
        root.addView(ctx.verticalSpacer(ctx.dp(26)))

        root.addView(ctx.sectionLabel("이벤트 로그"))
        root.addView(ctx.verticalSpacer(ctx.dp(10)))
        eventView = TextView(ctx).apply {
            setTextColor(Palette.TEXT_SUB)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setLineSpacing(ctx.dp(5).toFloat(), 1f)
            text = "아직 이벤트가 없어요"
        }
        val eventScroll = ScrollView(ctx).apply {
            addView(eventView)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ctx.dp(200)
            )
        }
        root.addView(ctx.roundedCard(eventScroll))

        return ScrollView(ctx).apply {
            setBackgroundColor(Palette.BG)
            isFillViewport = true
            addView(root)
        }
    }

    private fun buildHeader(): View {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ctx.screenTitle("러닝 트래킹").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            stateBadge = TextView(ctx).apply {
                text = "READY"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(ctx.dp(16), ctx.dp(7), ctx.dp(16), ctx.dp(7))
                background = pill(Palette.STATE_READY, ctx.dp(20).toFloat())
            }
            addView(stateBadge)
        }
    }

    private fun buildHeroCard(): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ctx.dp(20), ctx.dp(30), ctx.dp(20), ctx.dp(30))
            background = roundedBg(Palette.SURFACE, ctx.dp(20).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(TextView(ctx).apply {
            text = "거리"
            setTextColor(Palette.TEXT_SUB)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            letterSpacing = 0.12f
        })
        card.addView(ctx.verticalSpacer(ctx.dp(4)))
        distanceValue = TextView(ctx).apply {
            text = "0"
            setTextColor(Palette.TEXT_MAIN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 60f)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = -0.02f
            includeFontPadding = false
        }
        card.addView(distanceValue)
        card.addView(ctx.verticalSpacer(ctx.dp(4)))
        card.addView(TextView(ctx).apply {
            text = "meter"
            setTextColor(Palette.ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
        })
        return card
    }

    private fun buildStatsRow(): View {
        val ctx = requireContext()
        timeValue = statValue()
        currentPaceValue = statValue()
        avgPaceValue = statValue()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(statCell("시간", timeValue))
            addView(ctx.horizontalSpacer(ctx.dp(10)))
            addView(statCell("현재 페이스", currentPaceValue))
            addView(ctx.horizontalSpacer(ctx.dp(10)))
            addView(statCell("평균 페이스", avgPaceValue))
        }
    }

    private fun buildSubStatsRow(): View {
        val ctx = requireContext()
        smoothPaceValue = statValue(16f)
        gpsValue = statValue(16f)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(statCell("스무딩 페이스", smoothPaceValue))
            addView(ctx.horizontalSpacer(ctx.dp(10)))
            addView(statCell("GPS 정확도", gpsValue))
        }
    }

    private fun statCell(label: String, valueView: TextView): View {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(14), ctx.dp(14), ctx.dp(14), ctx.dp(14))
            background = roundedBg(Palette.SURFACE, ctx.dp(20).toFloat())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(ctx).apply {
                text = label
                setTextColor(Palette.TEXT_SUB)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            })
            addView(ctx.verticalSpacer(ctx.dp(7)))
            addView(valueView)
        }
    }

    private fun statValue(size: Float = 18f) = TextView(requireContext()).apply {
        text = "--"
        setTextColor(Palette.TEXT_MAIN)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTypeface(typeface, Typeface.BOLD)
    }

    // ---------------------------------------------------------------- 관찰

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.metrics.collect { renderMetrics(it) } }
                launch {
                    vm.eventLog.collect { log ->
                        eventView.text = if (log.isEmpty()) "아직 이벤트가 없어요"
                        else log.reversed().joinToString("\n")
                    }
                }
                launch {
                    vm.summaryText.collect { s -> if (s != null) showSummaryDialog(s) }
                }
            }
        }
    }

    private fun renderMetrics(m: RunningMetrics) {
        distanceValue.text = "%,.0f".format(m.totalDistanceMeter)
        timeValue.text = Format.duration(m.elapsedTimeSec)
        currentPaceValue.text = Format.pace(m.currentPaceSecPerKm)
        avgPaceValue.text = Format.pace(m.averagePaceSecPerKm)
        smoothPaceValue.text = Format.pace(m.smoothedPaceSecPerKm)
        gpsValue.text = "${"%.0f".format(m.gpsAccuracyMeter)} m"

        stateBadge.text = m.state.name
        stateBadge.background = pill(stateColor(m.state), requireContext().dp(20).toFloat())
        applyButtonState(m.state)
    }

    private fun applyButtonState(state: RunningState) {
        val running = state == RunningState.RUNNING
        val paused = state == RunningState.PAUSED
        val idle = state == RunningState.READY || state == RunningState.FINISHED
        setEnabled(startFakeBtn, idle)
        setEnabled(startGpsBtn, idle)
        setEnabled(pauseBtn, running)
        setEnabled(resumeBtn, paused)
        setEnabled(finishBtn, running || paused)
    }

    private fun setEnabled(v: TextView, enabled: Boolean) {
        v.isEnabled = enabled
        v.alpha = if (enabled) 1f else 0.35f
    }

    private fun showSummaryDialog(text: String) {
        summaryDialog?.dismiss()
        val ctx = requireContext()
        val content = TextView(ctx).apply {
            this.text = text
            setTextColor(Palette.TEXT_MAIN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.MONOSPACE
            setLineSpacing(ctx.dp(6).toFloat(), 1f)
            setPadding(ctx.dp(24), ctx.dp(24), ctx.dp(24), ctx.dp(8))
        }
        summaryDialog = AlertDialog.Builder(ctx)
            .setView(content)
            .setPositiveButton("확인") { d, _ -> d.dismiss() }
            .setOnDismissListener { vm.consumeSummary() }
            .show()
    }

    private fun stateColor(state: RunningState) = when (state) {
        RunningState.READY -> Palette.STATE_READY
        RunningState.RUNNING -> Palette.STATE_RUNNING
        RunningState.PAUSED -> Palette.STATE_PAUSED
        RunningState.FINISHED -> Palette.STATE_FINISHED
    }

    // ---------------------------------------------------------------- 권한/시작

    private fun onRealClicked() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startRealRun() else requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun startRealRun() {
        val provider = FusedLocationProvider(requireContext())
        vm.startRealRun(provider, RunningTarget(distanceMeter = 5000.0, paceSecPerKm = 360.0))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        summaryDialog?.dismiss()
        summaryDialog = null
    }
}
