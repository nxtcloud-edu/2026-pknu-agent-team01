package com.pknu.running.tracking.model

/**
 * 러닝 세션의 생명주기 상태.
 *
 * READY → RUNNING → PAUSED → RUNNING → FINISHED
 */
enum class RunningState {
    READY,
    RUNNING,
    PAUSED,
    FINISHED,
}
