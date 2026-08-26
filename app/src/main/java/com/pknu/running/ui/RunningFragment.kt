package com.pknu.running.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pknu.running.R
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

    private lateinit var narrationView: TextView
    private val modeChips = mutableMapOf<com.pknu.running.game.model.RunningMode, TextView>()

    private var summaryDialog: AlertDialog? = null

    // 러닝 중 교차로 보여줄 캐릭터 이미지
    private lateinit var runnerImage: ImageView
    private val runningFrames = intArrayOf(
        R.drawable.image0, R.drawable.image1, R.drawable.image2, R.drawable.image3, R.drawable.image4
    )
    private var frameIndex = 0
    private val frameHandler = Handler(Looper.getMainLooper())
    private val frameRunnable = object : Runnable {
        override fun run() {
            frameIndex = (frameIndex + 1) % runningFrames.size
            runnerImage.setImageResource(runningFrames[frameIndex])
            frameHandler.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }
    private var animating = false

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showGpsSetupDialog() }

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
        root.addView(ctx.verticalSpacer(ctx.dp(16)))

        // 모드 선택
        root.addView(buildModeSelector())
        root.addView(ctx.verticalSpacer(ctx.dp(18)))

        root.addView(buildHeroCard())
        root.addView(ctx.verticalSpacer(ctx.dp(12)))

        root.addView(buildStatsRow())
        root.addView(ctx.verticalSpacer(ctx.dp(10)))
        root.addView(buildSubStatsRow())
        root.addView(ctx.verticalSpacer(ctx.dp(24)))

        startFakeBtn = ctx.primaryButton("▶  러닝", Palette.ACCENT) { vm.startFakeRun() }
        startGpsBtn = ctx.primaryButton("＋  GPS 설정", Palette.SURFACE_2, Palette.TEXT_MAIN) { onRealClicked() }
        root.addView(ctx.buttonRow(54, startFakeBtn, startGpsBtn))
        root.addView(ctx.verticalSpacer(ctx.dp(10)))

        pauseBtn = ctx.secondaryButton("Ⅱ 일시정지") { vm.pause() }
        resumeBtn = ctx.secondaryButton("▶ 재개") { vm.resume() }
        finishBtn = ctx.secondaryButton("■ 종료") { vm.finish() }
        root.addView(ctx.buttonRow(54, pauseBtn, resumeBtn, finishBtn))
        root.addView(ctx.verticalSpacer(ctx.dp(26)))

        // 나레이션(TTS) 로그
        root.addView(ctx.sectionLabel("🎙 나레이션"))
        root.addView(ctx.verticalSpacer(ctx.dp(10)))
        narrationView = TextView(ctx).apply {
            setTextColor(Palette.TEXT_MAIN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(ctx.dp(6).toFloat(), 1f)
            text = "모드를 고르고 러닝을 시작하면 안내가 나와요"
        }
        val narrationScroll = ScrollView(ctx).apply {
            addView(narrationView)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ctx.dp(150)
            )
        }
        root.addView(ctx.roundedCard(narrationScroll))
        root.addView(ctx.verticalSpacer(ctx.dp(20)))

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

    private fun buildModeSelector(): View {
        val ctx = requireContext()
        val modes = listOf(
            com.pknu.running.game.model.RunningMode.BASIC to "기본",
            com.pknu.running.game.model.RunningMode.MARATHON to "마라톤",
            com.pknu.running.game.model.RunningMode.INTERVAL to "인터벌",
            com.pknu.running.game.model.RunningMode.NATIONAL_TEAM to "국가대표",
        )
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        // 2 x 2 그리드
        modes.chunked(2).forEachIndexed { rowIndex, rowModes ->
            if (rowIndex > 0) container.addView(ctx.verticalSpacer(ctx.dp(16)))
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            rowModes.forEachIndexed { i, (mode, label) ->
                if (i > 0) row.addView(ctx.horizontalSpacer(ctx.dp(10)))
                val chip = TextView(ctx).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(ctx.dp(12), ctx.dp(14), ctx.dp(12), ctx.dp(14))
                    isClickable = true
                    setOnClickListener { vm.setMode(mode) }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                modeChips[mode] = chip
                row.addView(chip)
            }
            container.addView(row)
        }
        return container
    }

    private fun updateModeChips(selected: com.pknu.running.game.model.RunningMode, enabled: Boolean) {
        val ctx = requireContext()
        modeChips.forEach { (mode, chip) ->
            val isSel = mode == selected
            chip.setTextColor(if (isSel) Palette.BG else Palette.TEXT_MAIN)
            chip.setTypeface(chip.typeface, if (isSel) Typeface.BOLD else Typeface.NORMAL)
            chip.background = if (isSel) roundedBg(Palette.ACCENT, ctx.dp(14).toFloat())
            else ctx.strokeBg(Palette.SURFACE_2, ctx.dp(14).toFloat())
            chip.isEnabled = enabled
            chip.alpha = if (enabled) 1f else 0.4f
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
        runnerImage = ImageView(ctx).apply {
            setImageResource(R.drawable.image_start)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ctx.dp(140)
            )
        }
        card.addView(runnerImage)
        card.addView(ctx.verticalSpacer(ctx.dp(16)))

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
                    vm.narrationLog.collect { log ->
                        narrationView.text = if (log.isEmpty()) "모드를 고르고 러닝을 시작하면 안내가 나와요"
                        else log.reversed().joinToString("\n\n")
                    }
                }
                launch {
                    vm.mode.collect { m -> updateModeChips(m, canChangeMode()) }
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
        // 러닝 중에는 모드 변경 불가
        updateModeChips(vm.mode.value, idle)
        updateRunnerImage(state)
    }

    /**
     * 러닝 상태에 따라 캐릭터 이미지를 갱신한다.
     * - 러닝 중(RUNNING): image0~4 를 교차로 애니메이션
     * - 그 외(READY/PAUSED/FINISHED, 즉 달리지 않는 동안): image_start 를 고정 표시
     */
    private fun updateRunnerImage(state: RunningState) {
        if (::runnerImage.isInitialized.not()) return
        if (state == RunningState.RUNNING) {
            startFrameAnimation()
        } else {
            stopFrameAnimation()
            runnerImage.setImageResource(R.drawable.image_start)
        }
    }

    private fun startFrameAnimation() {
        if (animating) return
        animating = true
        frameIndex = 0
        runnerImage.setImageResource(runningFrames[frameIndex])
        frameHandler.postDelayed(frameRunnable, FRAME_INTERVAL_MS)
    }

    private fun stopFrameAnimation() {
        if (!animating) return
        animating = false
        frameHandler.removeCallbacks(frameRunnable)
    }

    /** 모드 변경 가능 여부 (러닝 시작 전/종료 후에만). */
    private fun canChangeMode(): Boolean {
        val s = vm.state.value
        return s == RunningState.READY || s == RunningState.FINISHED
    }

    private fun setEnabled(v: TextView, enabled: Boolean) {
        v.isEnabled = enabled
        v.alpha = if (enabled) 1f else 0.35f
    }

    private fun showSummaryDialog(text: String) {
        summaryDialog?.dismiss()
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ctx.dp(24), ctx.dp(24), ctx.dp(24), ctx.dp(8))
        }
        // 러닝 종료 리포트 상단에 완주 이미지 표시
        container.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.image_finish)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ctx.dp(160)
            )
        })
        container.addView(ctx.verticalSpacer(ctx.dp(16)))
        container.addView(TextView(ctx).apply {
            this.text = text
            setTextColor(Palette.TEXT_MAIN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.MONOSPACE
            setLineSpacing(ctx.dp(6).toFloat(), 1f)
        })
        summaryDialog = AlertDialog.Builder(ctx)
            .setView(container)
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
        // 권한이 있으면 곧바로 목표 설정 창을, 없으면 권한 요청 후 목표 설정 창을 띄운다.
        if (granted) showGpsSetupDialog() else requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /**
     * GPS 러닝 시작 전 목표(거리·페이스)를 입력받는 설정 창.
     * 입력값은 모두 선택 사항이며, 비워두면 목표 없이 러닝을 시작한다.
     */
    private fun showGpsSetupDialog() {
        val ctx = requireContext()

        val distanceInput = EditText(ctx).apply {
            hint = "목표 거리 (km, 예: 5)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Palette.TEXT_MAIN)
            setHintTextColor(Palette.TEXT_SUB)
        }
        val paceInput = EditText(ctx).apply {
            hint = "목표 페이스 (분/km, 예: 6)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Palette.TEXT_MAIN)
            setHintTextColor(Palette.TEXT_SUB)
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(24), ctx.dp(16), ctx.dp(24), ctx.dp(8))
            addView(TextView(ctx).apply {
                text = "GPS로 실제 러닝을 시작합니다.\n목표를 설정하세요. (비워두면 목표 없음)"
                setTextColor(Palette.TEXT_SUB)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setLineSpacing(ctx.dp(4).toFloat(), 1f)
            })
            addView(ctx.verticalSpacer(ctx.dp(12)))
            addView(distanceInput)
            addView(ctx.verticalSpacer(ctx.dp(8)))
            addView(paceInput)
        }

        AlertDialog.Builder(ctx)
            .setTitle("GPS 러닝 설정")
            .setView(container)
            .setPositiveButton("시작") { d, _ ->
                val distanceKm = distanceInput.text.toString().trim().toDoubleOrNull()
                val paceMinPerKm = paceInput.text.toString().trim().toDoubleOrNull()
                val target = RunningTarget(
                    distanceMeter = distanceKm?.let { it * 1000.0 },
                    paceSecPerKm = paceMinPerKm?.let { it * 60.0 },
                )
                startRealRun(target)
                d.dismiss()
            }
            .setNegativeButton("취소") { d, _ -> d.dismiss() }
            .show()
    }

    private fun startRealRun(target: RunningTarget) {
        val provider = FusedLocationProvider(requireContext())
        vm.startRealRun(provider, target)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopFrameAnimation()
        summaryDialog?.dismiss()
        summaryDialog = null
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 300L
    }
}
