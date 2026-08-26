package com.pknu.running.ui

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pknu.running.music.model.MusicTag
import com.pknu.running.music.model.PlayMode
import com.pknu.running.music.model.PlaybackState
import com.pknu.running.music.model.TaggedTrack
import kotlinx.coroutines.launch

/**
 * 음악·플레이리스트 화면. 러닝 화면과 동일한 다크 테마 디자인을 사용한다.
 *
 * - 현재 곡 히어로 카드 (제목/아티스트/태그, 이벤트곡 배지)
 * - 재생 컨트롤 (재생 / 일시정지·재개 / 다음)
 * - 재생 모드 토글, 상황별 음악 규칙 프리셋
 * - 플레이리스트 목록
 */
class MusicFragment : Fragment() {

    private val vm: MusicViewModel by viewModels()

    private lateinit var nowTitle: TextView
    private lateinit var nowArtist: TextView
    private lateinit var nowTags: TextView
    private lateinit var stateBadge: TextView
    private lateinit var playBtn: TextView
    private lateinit var pauseResumeBtn: TextView
    private lateinit var nextBtn: TextView
    private lateinit var modeBtn: TextView
    private lateinit var ruleLabel: TextView
    private lateinit var listContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = buildUi()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.ui.collect { render(it) } }
            }
        }
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(20), ctx.dp(24), ctx.dp(20), ctx.dp(24))
        }

        // 헤더
        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ctx.screenTitle("음악").apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            stateBadge = TextView(ctx).apply {
                text = "IDLE"
                setTextColor(android.graphics.Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(ctx.dp(16), ctx.dp(7), ctx.dp(16), ctx.dp(7))
                background = pill(Palette.STATE_READY, ctx.dp(20).toFloat())
            }
            addView(stateBadge)
        })
        root.addView(ctx.verticalSpacer(ctx.dp(22)))

        // 현재 곡 히어로
        root.addView(buildNowPlaying())
        root.addView(ctx.verticalSpacer(ctx.dp(14)))

        // 재생 컨트롤
        playBtn = ctx.primaryButton("▶  재생", Palette.ACCENT) { vm.play() }
        nextBtn = ctx.primaryButton("⏭  다음", Palette.SURFACE_2, Palette.TEXT_MAIN) { vm.next() }
        root.addView(ctx.buttonRow(54, playBtn, nextBtn))
        root.addView(ctx.verticalSpacer(ctx.dp(10)))
        pauseResumeBtn = ctx.secondaryButton("Ⅱ 일시정지") { togglePauseResume() }
        modeBtn = ctx.secondaryButton("🔀 순차") { vm.togglePlayMode() }
        root.addView(ctx.buttonRow(54, pauseResumeBtn, modeBtn))
        root.addView(ctx.verticalSpacer(ctx.dp(20)))

        // 상황별 음악 규칙 프리셋
        root.addView(ctx.sectionLabel("상황별 음악"))
        root.addView(ctx.verticalSpacer(ctx.dp(4)))
        ruleLabel = TextView(ctx).apply {
            setTextColor(Palette.TEXT_SUB)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = "규칙: default"
        }
        root.addView(ruleLabel)
        root.addView(ctx.verticalSpacer(ctx.dp(10)))
        root.addView(ctx.buttonRow(46,
            ctx.secondaryButton("🔥 하이에너지") { vm.applyRule(MusicTag.HIGH_ENERGY, "high-energy") },
            ctx.secondaryButton("🌊 회복") { vm.applyRule(MusicTag.RECOVERY, "recovery") },
        ))
        root.addView(ctx.verticalSpacer(ctx.dp(8)))
        root.addView(ctx.buttonRow(46,
            ctx.secondaryButton("💗 러브") { vm.applyRule(MusicTag.LOVE, "love") },
            ctx.secondaryButton("↺ 기본") { vm.clearRule() },
        ))
        root.addView(ctx.verticalSpacer(ctx.dp(24)))

        // 플레이리스트
        root.addView(ctx.sectionLabel("플레이리스트"))
        root.addView(ctx.verticalSpacer(ctx.dp(10)))
        listContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        return ScrollView(ctx).apply {
            setBackgroundColor(Palette.BG)
            isFillViewport = true
            addView(root)
        }
    }

    private fun buildNowPlaying(): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(20), ctx.dp(24), ctx.dp(20), ctx.dp(24))
            background = roundedBg(Palette.SURFACE, ctx.dp(20).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(TextView(ctx).apply {
            text = "재생 중"
            setTextColor(Palette.TEXT_SUB)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            letterSpacing = 0.1f
        })
        card.addView(ctx.verticalSpacer(ctx.dp(8)))
        nowTitle = TextView(ctx).apply {
            text = "—"
            setTextColor(Palette.TEXT_MAIN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, Typeface.BOLD)
        }
        card.addView(nowTitle)
        nowArtist = TextView(ctx).apply {
            text = ""
            setTextColor(Palette.TEXT_SUB)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        card.addView(nowArtist)
        card.addView(ctx.verticalSpacer(ctx.dp(10)))
        nowTags = TextView(ctx).apply {
            text = ""
            setTextColor(Palette.ACCENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
        }
        card.addView(nowTags)
        return card
    }

    // ---------------------------------------------------------------- 렌더

    private fun render(s: MusicViewModel.MusicUiState) {
        nowTitle.text = s.currentTitle ?: "재생 대기 중"
        nowArtist.text = s.currentArtist ?: "재생 버튼을 눌러 시작하세요"
        nowTags.text = buildString {
            if (s.isEventTrack) append("★ 이벤트곡  ")
            append(s.currentTags.joinToString(" · ") { tagLabel(it) })
        }

        stateBadge.text = s.playbackState.name
        stateBadge.background = pill(playbackColor(s.playbackState), requireContext().dp(20).toFloat())

        pauseResumeBtn.text = if (s.playbackState == PlaybackState.PAUSED) "▶ 재개" else "Ⅱ 일시정지"
        modeBtn.text = if (s.playMode == PlayMode.SHUFFLE) "🔀 셔플" else "🔀 순차"
        ruleLabel.text = "규칙: ${s.rule}"

        renderList(s.playlist, s.currentTitle)
    }

    private fun renderList(list: List<TaggedTrack>, currentTitle: String?) {
        val ctx = requireContext()
        listContainer.removeAllViews()
        if (list.isEmpty()) {
            listContainer.addView(ctx.roundedCard(TextView(ctx).apply {
                text = "플레이리스트를 불러오는 중…"
                setTextColor(Palette.TEXT_SUB)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }))
            return
        }
        list.forEach { t ->
            val isPlaying = t.track.title == currentTitle
            listContainer.addView(trackRow(t, isPlaying))
            listContainer.addView(ctx.verticalSpacer(ctx.dp(8)))
        }
    }

    private fun trackRow(t: TaggedTrack, isPlaying: Boolean): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
            background = if (isPlaying) roundedBg(Palette.ACCENT_DIM, ctx.dp(14).toFloat())
            else roundedBg(Palette.SURFACE, ctx.dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val info = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(ctx).apply {
            text = t.track.title
            setTextColor(if (isPlaying) Palette.ACCENT else Palette.TEXT_MAIN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
        })
        info.addView(TextView(ctx).apply {
            text = "${t.track.artist}  ·  ${t.tags.joinToString(" ") { tagLabel(it) }}"
            setTextColor(Palette.TEXT_SUB)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        })
        row.addView(info)
        if (isPlaying) {
            row.addView(TextView(ctx).apply {
                text = "▶"
                setTextColor(Palette.ACCENT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            })
        }
        return row
    }

    private fun togglePauseResume() {
        if (vm.ui.value.playbackState == PlaybackState.PAUSED) vm.resume() else vm.pause()
    }

    private fun tagLabel(tag: MusicTag) = when (tag) {
        MusicTag.NORMAL -> "일반"
        MusicTag.HIGH_ENERGY -> "하이에너지"
        MusicTag.RECOVERY -> "회복"
        MusicTag.LOVE -> "러브"
        MusicTag.DRAMATIC -> "드라마틱"
        MusicTag.FAVORITE -> "즐겨찾기"
    }

    private fun playbackColor(s: PlaybackState) = when (s) {
        PlaybackState.IDLE -> Palette.STATE_READY
        PlaybackState.PLAYING -> Palette.STATE_RUNNING
        PlaybackState.PAUSED -> Palette.STATE_PAUSED
        PlaybackState.LOADING -> Palette.STATE_FINISHED
    }
}
