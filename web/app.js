"use strict";

/* =========================================================================
 * RunningApp Web (최소 버전)
 * 안드로이드 앱과 동일한 개념을 순수 JS로 옮긴 데모.
 * - 상태: READY / RUNNING / PAUSED / FINISHED
 * - 지표: 거리(m), 시간(s), 현재/평균 페이스(sec/km)
 * - 모드별 간단 나레이션 + Web Speech TTS
 * - GPS(Geolocation) 또는 가상 러닝(Fake)으로 구동
 * ========================================================================= */

// ---------------------------------------------------------------- 포맷 (Format.kt 대응)
function formatDuration(totalSec) {
  const m = Math.floor(totalSec / 60);
  const s = Math.floor(totalSec % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}
function formatPace(secPerKm) {
  if (secPerKm == null || !isFinite(secPerKm) || isNaN(secPerKm)) return `--'--"`;
  const m = Math.floor(secPerKm / 60);
  const s = Math.floor(secPerKm % 60);
  return `${m}'${String(s).padStart(2, "0")}"/km`;
}

// ---------------------------------------------------------------- 거리 (Haversine)
function haversineMeters(a, b) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLon = toRad(b.lon - a.lon);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

// ---------------------------------------------------------------- 모드별 나레이션
const MODE_LABEL = {
  BASIC: "기본",
  MARATHON: "마라톤",
  INTERVAL: "인터벌",
  NATIONAL_TEAM: "국가대표",
};

// 500m 마다 재생할 모드별 안내 문구 생성기
function distanceNarration(mode, distanceMeter, avgPaceSecPerKm) {
  // 1km 미만은 미터로, 이상은 km로 표기
  const km = distanceMeter < 1000
    ? `${Math.round(distanceMeter)}m`
    : `${(distanceMeter / 1000).toFixed(1)}km`;
  const pace = formatPace(avgPaceSecPerKm);
  switch (mode) {
    case "MARATHON":
      return `${km} 통과. 페이스 ${pace} 유지하며 리듬을 지켜요.`;
    case "INTERVAL":
      return `${km}. 다음 구간까지 힘을 아껴 두세요.`;
    case "NATIONAL_TEAM":
      return `중계석입니다! ${km} 지점, 현재 페이스 ${pace}! 선두권을 노려봅니다!`;
    default:
      return `${km} 지났어요. 평균 페이스 ${pace}.`;
  }
}

// 랜덤 이벤트 문구 (RandomEventType 대응)
const RANDOM_EVENTS = [
  "앞에 잘생긴 사람이 나타났습니다.",
  "라이벌이 따라오고 있습니다.",
  "지금부터 30초 스퍼트!",
  "이번 노래가 끝날 때까지 달려볼까요?",
];

// ---------------------------------------------------------------- 상태
const State = { READY: "READY", RUNNING: "RUNNING", PAUSED: "PAUSED", FINISHED: "FINISHED" };

const app = {
  state: State.READY,
  mode: "BASIC",
  startTs: 0,
  elapsedSec: 0,
  distanceMeter: 0,
  lastPoint: null,
  lastPaceSecPerKm: null,
  events: [],
  narrations: [],
  lastNarrationKm: 0,
  timer: null,
  geoWatchId: null,
  fake: null, // 가상 러닝 인터벌
};

// ---------------------------------------------------------------- DOM
const $ = (id) => document.getElementById(id);
const el = {
  stateBadge: $("stateBadge"),
  runnerImg: $("runnerImg"),
  distance: $("distanceValue"),
  time: $("timeValue"),
  curPace: $("curPaceValue"),
  avgPace: $("avgPaceValue"),
  startFake: $("startFakeBtn"),
  startGps: $("startGpsBtn"),
  pause: $("pauseBtn"),
  resume: $("resumeBtn"),
  finish: $("finishBtn"),
  narration: $("narration"),
  events: $("events"),
  summaryBackdrop: $("summaryBackdrop"),
  summaryText: $("summaryText"),
  summaryClose: $("summaryCloseBtn"),
  modeGrid: $("modeGrid"),
};

// ---------------------------------------------------------------- 러너 이미지 애니메이션
const RUN_FRAMES = ["image0", "image1", "image2", "image3", "image4"];
let frameIdx = 0;
let frameTimer = null;
function setRunnerImage(name) {
  el.runnerImg.src = `assets/${name}.png`;
}
function startFrameAnim() {
  if (frameTimer) return;
  frameIdx = 0;
  setRunnerImage(RUN_FRAMES[0]);
  frameTimer = setInterval(() => {
    frameIdx = (frameIdx + 1) % RUN_FRAMES.length;
    setRunnerImage(RUN_FRAMES[frameIdx]);
  }, 300);
}
function stopFrameAnim() {
  if (frameTimer) { clearInterval(frameTimer); frameTimer = null; }
  setRunnerImage("image_start");
}

// ---------------------------------------------------------------- TTS
let activeSpeechCount = 0; // 동시에 진행 중인 나레이션 수

function speak(text) {
  if (!("speechSynthesis" in window)) return;
  const u = new SpeechSynthesisUtterance(text);
  u.lang = "ko-KR";

  // 나레이션이 나오는 동안 음악 볼륨을 낮추고(더킹), 끝나면 복원한다.
  u.onstart = () => {
    activeSpeechCount++;
    YT_PLAYER.duck();
  };
  const onDone = () => {
    activeSpeechCount = Math.max(0, activeSpeechCount - 1);
    // 마지막 나레이션이 끝났을 때만 볼륨 복원
    if (activeSpeechCount === 0) YT_PLAYER.restore();
  };
  u.onend = onDone;
  u.onerror = onDone;

  window.speechSynthesis.speak(u);
}

function addNarration(text) {
  app.narrations.push(text);
  el.narration.textContent = app.narrations.slice().reverse().join("\n\n");
  speak(text);
}

function addEvent(text) {
  const stamp = formatDuration(app.elapsedSec);
  app.events.push(`[${stamp}] ${text}`);
  el.events.textContent = app.events.slice().reverse().join("\n");
}

// ---------------------------------------------------------------- 렌더링
function stateColorClass(state) {
  switch (state) {
    case State.RUNNING: return "badge-running";
    case State.PAUSED: return "badge-paused";
    case State.FINISHED: return "badge-finished";
    default: return "badge-ready";
  }
}

function render() {
  el.distance.textContent = Math.round(app.distanceMeter).toLocaleString();
  el.time.textContent = formatDuration(app.elapsedSec);
  el.curPace.textContent = formatPace(app.lastPaceSecPerKm);
  const avg = app.distanceMeter > 0 ? (app.elapsedSec / (app.distanceMeter / 1000)) : null;
  el.avgPace.textContent = formatPace(avg);

  el.stateBadge.textContent = app.state;
  el.stateBadge.className = "badge " + stateColorClass(app.state);

  applyButtonState();
  updateRunnerImage();
}

function applyButtonState() {
  const running = app.state === State.RUNNING;
  const paused = app.state === State.PAUSED;
  const idle = app.state === State.READY || app.state === State.FINISHED;
  el.startFake.disabled = !idle;
  el.startGps.disabled = !idle;
  el.pause.disabled = !running;
  el.resume.disabled = !paused;
  el.finish.disabled = !(running || paused);
  // 러닝 중에는 모드 변경 불가
  document.querySelectorAll(".chip").forEach((c) => (c.disabled = !idle));
}

function updateRunnerImage() {
  if (app.state === State.RUNNING) startFrameAnim();
  else stopFrameAnim();
}

// ---------------------------------------------------------------- 지표 갱신
function onNewPoint(lat, lon) {
  if (app.state !== State.RUNNING) return;
  const point = { lat, lon, t: Date.now() };
  if (app.lastPoint) {
    const d = haversineMeters(app.lastPoint, point);
    // 튀는 값(순간 이동) 방지: 0~50m 사이만 반영
    if (d >= 0 && d < 50) {
      app.distanceMeter += d;
      const dtSec = (point.t - app.lastPoint.t) / 1000;
      if (dtSec > 0 && d > 0) {
        app.lastPaceSecPerKm = dtSec / (d / 1000);
      }
    }
  }
  app.lastPoint = point;
  maybeDistanceNarration();
  render();
}

function maybeDistanceNarration() {
  const milestone = Math.floor(app.distanceMeter / 40) * 40; // 40m 단위
  if (milestone >= 40 && milestone > app.lastNarrationKm) {
    app.lastNarrationKm = milestone;
    const avg = app.distanceMeter > 0 ? app.elapsedSec / (app.distanceMeter / 1000) : null;
    addNarration(distanceNarration(app.mode, app.distanceMeter, avg));
    addEvent(`DISTANCE_MILESTONE (${Math.round(app.distanceMeter)}m)`);

    // 국가대표 모드는 가끔 랜덤 이벤트도 발생
    if (app.mode === "NATIONAL_TEAM" && Math.random() < 0.5) {
      const ev = RANDOM_EVENTS[Math.floor(Math.random() * RANDOM_EVENTS.length)];
      addNarration(ev);
      addEvent("RANDOM_EVENT");
    }
  }
}

// ---------------------------------------------------------------- 타이머
function startTicker() {
  app.startTs = Date.now() - app.elapsedSec * 1000;
  app.timer = setInterval(() => {
    app.elapsedSec = Math.floor((Date.now() - app.startTs) / 1000);
    render();
  }, 1000);
}
function stopTicker() {
  if (app.timer) { clearInterval(app.timer); app.timer = null; }
}

// ---------------------------------------------------------------- 제어
function resetRun() {
  stopTicker();
  stopGeo();
  stopFake();
  app.elapsedSec = 0;
  app.distanceMeter = 0;
  app.lastPoint = null;
  app.lastPaceSecPerKm = null;
  app.events = [];
  app.narrations = [];
  app.lastNarrationKm = 0;
  el.narration.textContent = "모드를 고르고 러닝을 시작하면 안내가 나와요";
  el.events.textContent = "아직 이벤트가 없어요";
}

function begin() {
  app.state = State.RUNNING;
  startTicker();
  addNarration(`${MODE_LABEL[app.mode]} 모드로 러닝을 시작합니다. 화이팅!`);
  addEvent("RUN_STARTED");
  render();
}

function startFakeRun() {
  resetRun();
  begin();
  // 6'30" → 5'30" → 4'50" 로 빨라지는 시나리오 (앱 startFakeRun 대응)
  const segments = [
    { sec: 60, pace: 390 },
    { sec: 60, pace: 330 },
    { sec: 999, pace: 290 },
  ];
  let baseLat = 35.1341, baseLon = 129.1058; // 부산 근처 임의 시작점
  app.lastPoint = { lat: baseLat, lon: baseLon, t: Date.now() };
  app.fake = setInterval(() => {
    if (app.state !== State.RUNNING) return;
    // 현재 구간 페이스 선택
    let acc = 0, pace = 290;
    for (const s of segments) { acc += s.sec; if (app.elapsedSec < acc) { pace = s.pace; break; } }
    // 1초 동안 이동 거리(m) = 1000 / pace * 1
    const stepMeter = 1000 / pace;
    // 위도 방향으로 이동 (1도 ≈ 111,320m)
    const dLat = stepMeter / 111320;
    const next = { lat: app.lastPoint.lat + dLat, lon: app.lastPoint.lon, t: Date.now() };
    onNewPoint(next.lat, next.lon);
  }, 1000);
}

function startGpsRun() {
  if (!("geolocation" in navigator)) {
    alert("이 브라우저는 위치 기능을 지원하지 않습니다. '러닝(가상)'으로 체험해 보세요.");
    return;
  }
  resetRun();
  begin();
  app.geoWatchId = navigator.geolocation.watchPosition(
    (pos) => onNewPoint(pos.coords.latitude, pos.coords.longitude),
    (err) => {
      addEvent(`GPS_ERROR (${err.code})`);
      alert("위치 권한이 필요합니다. 권한을 허용하거나 '러닝(가상)'으로 체험해 보세요.");
    },
    { enableHighAccuracy: true, maximumAge: 1000, timeout: 10000 }
  );
}

function stopGeo() {
  if (app.geoWatchId != null) { navigator.geolocation.clearWatch(app.geoWatchId); app.geoWatchId = null; }
}
function stopFake() {
  if (app.fake) { clearInterval(app.fake); app.fake = null; }
}

function pauseRun() {
  if (app.state !== State.RUNNING) return;
  app.state = State.PAUSED;
  stopTicker();
  addEvent("PAUSED");
  render();
}

function resumeRun() {
  if (app.state !== State.PAUSED) return;
  app.state = State.RUNNING;
  startTicker();
  addEvent("RESUMED");
  render();
}

function finishRun() {
  if (app.state !== State.RUNNING && app.state !== State.PAUSED) return;
  stopTicker();
  stopGeo();
  stopFake();
  app.state = State.FINISHED;
  const avg = app.distanceMeter > 0 ? app.elapsedSec / (app.distanceMeter / 1000) : null;
  addEvent("RUN_FINISHED");
  render();
  showSummary(avg);
}

function showSummary(avgPace) {
  const text =
    "=== 러닝 요약 ===\n" +
    `모드: ${MODE_LABEL[app.mode]}\n` +
    `거리: ${Math.round(app.distanceMeter)} m\n` +
    `시간: ${formatDuration(app.elapsedSec)}\n` +
    `평균 페이스: ${formatPace(avgPace)}\n` +
    `이벤트 수: ${app.events.length}`;
  el.summaryText.textContent = text;
  el.summaryBackdrop.classList.remove("hidden");

  // 거리가 있을 때만 기록으로 저장 (앱 finish() 동작 대응)
  if (app.distanceMeter > 0) {
    History.add({
      epochMillis: Date.now(),
      totalDistanceMeter: app.distanceMeter,
      elapsedTimeSec: app.elapsedSec,
      averagePaceSecPerKm: avgPace,
      eventCount: app.events.length,
    });
  }
}

// ---------------------------------------------------------------- 이벤트 바인딩
document.querySelectorAll(".chip").forEach((chip) => {
  chip.addEventListener("click", () => {
    if (app.state !== State.READY && app.state !== State.FINISHED) return;
    app.mode = chip.dataset.mode;
    document.querySelectorAll(".chip").forEach((c) => c.classList.remove("selected"));
    chip.classList.add("selected");
  });
});

el.startFake.addEventListener("click", startFakeRun);
el.startGps.addEventListener("click", startGpsRun);
el.pause.addEventListener("click", pauseRun);
el.resume.addEventListener("click", resumeRun);
el.finish.addEventListener("click", finishRun);
el.summaryClose.addEventListener("click", () => el.summaryBackdrop.classList.add("hidden"));

// ---------------------------------------------------------------- 초기화
document.querySelector('.chip[data-mode="BASIC"]').classList.add("selected");
setRunnerImage("image_start");
render();

/* =========================================================================
 * 하단 네비게이션 — 페이지 전환
 * ========================================================================= */
const Nav = {
  show(page) {
    document.querySelectorAll(".page").forEach((p) => p.classList.add("hidden"));
    document.getElementById(`page-${page}`).classList.remove("hidden");
    document.querySelectorAll(".nav-tab").forEach((t) => {
      t.classList.toggle("active", t.dataset.page === page);
    });
    if (page === "history") Calendar.render();
    window.scrollTo(0, 0);
  },
};
document.querySelectorAll(".nav-tab").forEach((tab) => {
  tab.addEventListener("click", () => Nav.show(tab.dataset.page));
});

/* =========================================================================
 * 음악 페이지 (MusicFragment 대응, 오디오 없이 목업 재생 상태만)
 * ========================================================================= */
const TAG_LABEL = {
  NORMAL: "일반", HIGH_ENERGY: "하이에너지", RECOVERY: "회복",
  LOVE: "러브", DRAMATIC: "드라마틱", FAVORITE: "즐겨찾기",
};

// 각 트랙에 YouTube 영상 ID를 연결한다. 재생 시 YouTube IFrame Player로 실제 재생.
// (NoCopyrightSounds 등 오래 유지되는 로열티프리 음원 위주)
const ALL_TRACKS = [
  { title: "Sprint Fire", artist: "Neon Pulse", tags: ["HIGH_ENERGY"], videoId: "K4DyBUG242c" },   // NCS - Cartoon On&On
  { title: "Uphill Battle", artist: "Cadence", tags: ["HIGH_ENERGY", "DRAMATIC"], videoId: "1w7OgIMMRc4" }, // NCS - Sugar Crash
  { title: "Steady Flow", artist: "River Kim", tags: ["NORMAL"], videoId: "9iHM6X6uUH8" },          // NCS - Spektrem Shine
  { title: "Cool Down", artist: "Aloe", tags: ["RECOVERY"], videoId: "CevxZvSJLk8" },              // Katy Perry - Roar
  { title: "Morning Breeze", artist: "Aloe", tags: ["RECOVERY", "NORMAL"], videoId: "yJg-Y5byMMw" }, // NCS - Elektronomia Sky High
  { title: "First Love Run", artist: "Sugar Beat", tags: ["LOVE"], videoId: "fJ9rUzIMcZQ" },       // Queen - Don't Stop Me Now
  { title: "Final Lap", artist: "Cadence", tags: ["DRAMATIC", "HIGH_ENERGY"], videoId: "mWRsgZuwf_8" }, // Survivor - Eye of the Tiger
  { title: "Golden Hour", artist: "Sugar Beat", tags: ["LOVE", "NORMAL"], videoId: "60ItHLz5WEA" },  // Alan Walker - Faded
];

/* -------------------------------------------------------------------------
 * YouTube IFrame Player — 실제 오디오 재생
 * 앱의 "YouTube 재생" 컨셉을 웹에서 공식 IFrame API로 구현.
 * ------------------------------------------------------------------------- */
const YT_PLAYER = {
  player: null,
  ready: false,
  pendingVideoId: null,

  init() {
    // IFrame API 스크립트 로드
    const tag = document.createElement("script");
    tag.src = "https://www.youtube.com/iframe_api";
    document.head.appendChild(tag);
    // API가 준비되면 호출되는 전역 콜백
    window.onYouTubeIframeAPIReady = () => {
      this.player = new YT.Player("ytPlayer", {
        height: "1",
        width: "1",
        playerVars: { autoplay: 0, controls: 0, playsinline: 1 },
        events: {
          onReady: () => {
            this.ready = true;
            if (this.pendingVideoId) {
              const id = this.pendingVideoId;
              this.pendingVideoId = null;
              this.load(id);
            }
          },
          onStateChange: (e) => {
            // 곡이 끝나면(0) 자동으로 다음 곡
            if (e.data === YT.PlayerState.ENDED) Music.next();
          },
          onError: () => {
            // 임베드 불가/삭제된 영상이면 다음 곡으로 자동 스킵
            if (Music.state === "PLAYING") Music.next();
          },
        },
      });
    };
  },

  load(videoId) {
    if (!videoId) return;
    if (!this.ready || !this.player) { this.pendingVideoId = videoId; return; }
    this.player.loadVideoById(videoId); // 로드 후 자동 재생 시작
  },
  play() { if (this.ready && this.player) this.player.playVideo(); },
  pause() { if (this.ready && this.player) this.player.pauseVideo(); },
  stop() { if (this.ready && this.player) this.player.stopVideo(); },

  // --- 오디오 더킹 (앱 AudioDucker 대응) ---
  ducked: false,
  normalVolume: 100, // 0~100
  duckVolume: 5,     // 나레이션 중 낮출 볼륨 (확 줄임)

  duck() {
    if (!this.ready || !this.player || this.ducked) return;
    this.ducked = true;
    // 현재 볼륨을 기억했다가 낮춘다
    try { this.normalVolume = this.player.getVolume(); } catch (e) {}
    this.player.setVolume(this.duckVolume);
  },
  restore() {
    if (!this.ready || !this.player || !this.ducked) return;
    this.ducked = false;
    this.player.setVolume(this.normalVolume);
  },
};

const Music = {
  state: "IDLE", // IDLE / PLAYING / PAUSED
  mode: "SEQ", // SEQ / SHUFFLE
  loadedVideoId: null, // 현재 플레이어에 로드된 영상 id
  rule: "default",
  filterTag: null,
  index: 0,

  el: {
    badge: $("musicBadge"),
    title: $("nowTitle"),
    artist: $("nowArtist"),
    tags: $("nowTags"),
    play: $("mPlayBtn"),
    next: $("mNextBtn"),
    pause: $("mPauseBtn"),
    modeBtn: $("mModeBtn"),
    ruleLabel: $("ruleLabel"),
    playlist: $("playlist"),
  },

  list() {
    if (!this.filterTag) return ALL_TRACKS;
    return ALL_TRACKS.filter((t) => t.tags.includes(this.filterTag));
  },

  current() {
    const list = this.list();
    if (list.length === 0) return null;
    return list[this.index % list.length];
  },

  play() {
    if (this.list().length === 0) return;
    this.state = "PLAYING";
    this.render();
  },

  togglePause() {
    if (this.state === "PLAYING") this.state = "PAUSED";
    else if (this.state === "PAUSED") this.state = "PLAYING";
    this.render();
  },

  next() {
    const list = this.list();
    if (list.length === 0) return;
    if (this.mode === "SHUFFLE") this.index = Math.floor(Math.random() * list.length);
    else this.index = (this.index + 1) % list.length;
    if (this.state === "IDLE") this.state = "PLAYING";
    this.render();
  },

  toggleMode() {
    this.mode = this.mode === "SEQ" ? "SHUFFLE" : "SEQ";
    this.render();
  },

  applyRule(tag, rule) {
    this.filterTag = tag;
    this.rule = rule;
    this.index = 0;
    this.render();
  },

  clearRule() {
    this.filterTag = null;
    this.rule = "default";
    this.index = 0;
    this.render();
  },

  badgeClass() {
    switch (this.state) {
      case "PLAYING": return "badge-running";
      case "PAUSED": return "badge-paused";
      default: return "badge-ready";
    }
  },

  render() {
    const cur = this.current();
    this.el.title.textContent = cur ? cur.title : "재생 대기 중";
    this.el.artist.textContent = cur ? cur.artist : "재생 버튼을 눌러 시작하세요";
    this.el.tags.textContent = cur ? cur.tags.map((t) => TAG_LABEL[t]).join(" · ") : "";

    this.el.badge.textContent = this.state;
    this.el.badge.className = "badge " + this.badgeClass();

    this.el.pause.textContent = this.state === "PAUSED" ? "▶ 재개" : "Ⅱ 일시정지";
    this.el.modeBtn.textContent = this.mode === "SHUFFLE" ? "🔀 셔플" : "🔀 순차";
    this.el.ruleLabel.textContent = `규칙: ${this.rule}`;

    // 플레이리스트
    const list = this.list();
    const curTitle = cur ? cur.title : null;
    this.el.playlist.innerHTML = "";
    list.forEach((t) => {
      const playing = t.title === curTitle && this.state !== "IDLE";
      const row = document.createElement("div");
      row.className = "track" + (playing ? " playing" : "");
      row.innerHTML =
        `<div class="track-info">` +
        `<div class="track-title">${t.title}</div>` +
        `<div class="track-sub">${t.artist}  ·  ${t.tags.map((x) => TAG_LABEL[x]).join(" ")}</div>` +
        `</div>` +
        (playing ? `<div class="track-play">▶</div>` : "");
      row.addEventListener("click", () => {
        this.index = list.indexOf(t);
        this.state = "PLAYING";
        this.render();
      });
      this.el.playlist.appendChild(row);
    });

    this.syncPlayback(cur);
  },

  // UI 상태(state/현재 곡)를 실제 YouTube 플레이어에 반영한다.
  syncPlayback(cur) {
    if (this.state === "IDLE" || !cur) {
      YT_PLAYER.stop();
      this.loadedVideoId = null;
      return;
    }
    // 곡이 바뀌었으면 새로 로드(로드 시 자동 재생)
    if (cur.videoId && cur.videoId !== this.loadedVideoId) {
      this.loadedVideoId = cur.videoId;
      YT_PLAYER.load(cur.videoId);
    }
    // 같은 곡에서 재생/일시정지 토글
    if (this.state === "PLAYING") YT_PLAYER.play();
    else if (this.state === "PAUSED") YT_PLAYER.pause();
  },
};

YT_PLAYER.init();
Music.el.play.addEventListener("click", () => Music.play());
Music.el.next.addEventListener("click", () => Music.next());
Music.el.pause.addEventListener("click", () => Music.togglePause());
Music.el.modeBtn.addEventListener("click", () => Music.toggleMode());
document.querySelectorAll(".rule-btn").forEach((btn) => {
  btn.addEventListener("click", () => {
    if (btn.dataset.rule === "default") Music.clearRule();
    else Music.applyRule(btn.dataset.tag, btn.dataset.rule);
  });
});
Music.render();

/* =========================================================================
 * 기록 페이지 (CalendarFragment + RunHistoryStore 대응, localStorage 사용)
 * ========================================================================= */
const History = {
  KEY: "runningapp_history",

  load() {
    try { return JSON.parse(localStorage.getItem(this.KEY)) || []; }
    catch { return []; }
  },
  save(list) { localStorage.setItem(this.KEY, JSON.stringify(list)); },

  add(entry) {
    const list = this.load();
    list.push(entry);
    this.save(list);
  },
  clear() { this.save([]); },

  dateKeyOf(epochMillis) {
    const d = new Date(epochMillis);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
  },

  loadByDate(dateKey) {
    return this.load()
      .filter((e) => this.dateKeyOf(e.epochMillis) === dateKey)
      .sort((a, b) => a.epochMillis - b.epochMillis);
  },

  totalDurationByDate() {
    const map = {};
    this.load().forEach((e) => {
      const k = this.dateKeyOf(e.epochMillis);
      map[k] = (map[k] || 0) + e.elapsedTimeSec;
    });
    return map;
  },
};

const LEVEL2_SEC = 15 * 60;
const LEVEL3_SEC = 30 * 60;

// 기록 페이지 임시 색칠용 데이터. 실제 기록과 별개로 항상 함께 표시된다.
// (다 채우지 않고 드문드문 — 연함/중간/진함 3단계가 모두 보이도록 구성)
// [일자, 러닝 분]
const SEED_DAYS = [
  [2, 8],   // 연함
  [5, 20],  // 중간
  [9, 40],  // 진함
  [13, 12], // 연함
  [16, 33], // 진함
  [22, 24], // 중간
  [27, 45], // 진함
  // 추가 6개
  [3, 10],  // 연함
  [7, 28],  // 중간
  [11, 50], // 진함
  [18, 9],  // 연함
  [24, 22], // 중간
  [29, 37], // 진함
];

const Calendar = {
  viewYear: new Date().getFullYear(),
  viewMonth: new Date().getMonth(), // 0-based
  selectedKey: History.dateKeyOf(Date.now()),

  el: {
    monthLabel: $("monthLabel"),
    grid: $("calGrid"),
    detailTitle: $("detailTitle"),
    detail: $("detail"),
  },

  levelOf(sec) {
    if (sec <= 0) return 0;
    if (sec < LEVEL2_SEC) return 1;
    if (sec < LEVEL3_SEC) return 2;
    return 3;
  },

  changeMonth(delta) {
    this.viewMonth += delta;
    if (this.viewMonth < 0) { this.viewMonth = 11; this.viewYear--; }
    else if (this.viewMonth > 11) { this.viewMonth = 0; this.viewYear++; }
    this.render();
  },

  // 현재 보고 있는 달의 임시 색칠 데이터를 dateKey→duration(sec) 맵으로 만든다.
  seedDurationMap() {
    const map = {};
    const maxDay = new Date(this.viewYear, this.viewMonth + 1, 0).getDate();
    SEED_DAYS.forEach(([day, minutes]) => {
      if (day > maxDay) return;
      const key = `${this.viewYear}-${String(this.viewMonth + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
      map[key] = (map[key] || 0) + minutes * 60;
    });
    return map;
  },

  render() {
    // 실제 기록 + 임시 색칠 데이터를 합쳐서 색칠한다 (localStorage 상태와 무관하게 항상 표시).
    const durByDate = History.totalDurationByDate();
    const seedMap = this.seedDurationMap();
    Object.entries(seedMap).forEach(([k, v]) => {
      durByDate[k] = (durByDate[k] || 0) + v;
    });
    this.el.monthLabel.textContent = `${this.viewYear}년 ${this.viewMonth + 1}월`;

    const firstWeekday = new Date(this.viewYear, this.viewMonth, 1).getDay(); // 0=일
    const daysInMonth = new Date(this.viewYear, this.viewMonth + 1, 0).getDate();

    this.el.grid.innerHTML = "";
    // 앞쪽 빈칸
    for (let i = 0; i < firstWeekday; i++) {
      const empty = document.createElement("div");
      empty.className = "cell empty";
      this.el.grid.appendChild(empty);
    }
    // 날짜 셀
    for (let day = 1; day <= daysInMonth; day++) {
      const key = `${this.viewYear}-${String(this.viewMonth + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
      const level = this.levelOf(durByDate[key] || 0);
      const cell = document.createElement("div");
      cell.className = "cell" + (level ? ` lvl${level}` : "") + (key === this.selectedKey ? " selected" : "");
      cell.textContent = day;
      cell.addEventListener("click", () => {
        this.selectedKey = key;
        this.render();
      });
      this.el.grid.appendChild(cell);
    }
    this.renderDetail();
  },

  renderDetail() {
    const [y, m, d] = this.selectedKey.split("-").map(Number);
    this.el.detailTitle.textContent = `${m}월 ${d}일 기록`;
    const entries = History.loadByDate(this.selectedKey);
    this.el.detail.innerHTML = "";
    if (entries.length === 0) {
      // 실제 기록이 없어도 임시 색칠 데이터가 있는 날이면 임시 요약을 보여준다.
      const seedMap = this.seedDurationMap();
      const seedSec = seedMap[this.selectedKey];
      const card = document.createElement("div");
      card.className = "entry-card";
      if (seedSec) {
        card.textContent =
          `09:00 러닝\n` +
          `거리  ${Math.round((seedSec / 60) * 1000 / 6).toLocaleString()} m\n` +
          `시간  ${formatDuration(seedSec)}\n` +
          `평균 페이스  ${formatPace(360)}\n` +
          `이벤트  ${Math.floor(seedSec / 60 / 5)}건`;
      } else {
        card.style.color = "var(--text-sub)";
        card.textContent = "이 날의 러닝 기록이 없어요";
      }
      this.el.detail.appendChild(card);
      return;
    }
    entries.forEach((e) => {
      const t = new Date(e.epochMillis);
      const hhmm = `${String(t.getHours()).padStart(2, "0")}:${String(t.getMinutes()).padStart(2, "0")}`;
      const card = document.createElement("div");
      card.className = "entry-card";
      card.textContent =
        `${hhmm} 러닝\n` +
        `거리  ${Math.round(e.totalDistanceMeter).toLocaleString()} m\n` +
        `시간  ${formatDuration(e.elapsedTimeSec)}\n` +
        `평균 페이스  ${formatPace(e.averagePaceSecPerKm)}\n` +
        `이벤트  ${e.eventCount}건`;
      this.el.detail.appendChild(card);
    });
  },

  // 발표/데모용 샘플 데이터 (이번 달)
  fillSample() {
    History.clear();
    const samples = [
      [1, 8], [2, 6], [3, 14], [5, 12], [6, 33], [7, 5], [8, 18], [9, 27],
      [11, 9], [12, 24], [13, 16], [14, 25], [15, 38], [16, 7], [17, 30], [18, 13],
      [20, 15], [21, 42], [22, 11], [23, 35], [24, 19], [25, 28], [26, 22],
      [28, 31], [29, 40], [31, 45], [6, 20], [14, 15], [23, 12],
    ];
    const maxDay = new Date(this.viewYear, this.viewMonth + 1, 0).getDate();
    const sessionCount = {};
    samples.forEach(([day, minutes]) => {
      if (day > maxDay) return;
      const c = sessionCount[day] || 0;
      sessionCount[day] = c + 1;
      const hour = 8 + c * 3;
      const epoch = new Date(this.viewYear, this.viewMonth, day, hour, 0, 0).getTime();
      History.add({
        epochMillis: epoch,
        totalDistanceMeter: (minutes * 1000) / 6,
        elapsedTimeSec: minutes * 60,
        averagePaceSecPerKm: 360,
        eventCount: Math.floor(minutes / 5),
      });
    });
    this.render();
  },
};

$("prevMonth").addEventListener("click", () => Calendar.changeMonth(-1));
$("nextMonth").addEventListener("click", () => Calendar.changeMonth(1));
$("sampleBtn").addEventListener("click", () => Calendar.fillSample());
