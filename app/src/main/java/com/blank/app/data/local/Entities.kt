package com.blank.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 지식 항목의 종류. 무엇을 "재현 성공" 으로 볼지가 종류마다 다르므로
 * 채점 프롬프트가 이 값을 보고 기준을 바꾼다.
 */
enum class ItemKind { STRUCTURE, LIST, SEQUENCE, VALUE }

/** 회차 상태. NOTIFIED 는 "알림이 나갔고 원본이 그 시점 리비전으로 못박혔다" 는 뜻. */
enum class RoundState { PENDING, NOTIFIED, DONE, SKIPPED }

/** 채점 등급. 3단계 이상은 자기채점 노이즈만 늘린다. */
enum class Grade { GREEN, AMBER, RED, UNGRADED }

/** 재현물의 형태. 사진만, 글만, 둘 다 모두 허용한다. */
enum class SubmissionKind { TEXT, IMAGE, BOTH, GAVE_UP }

/** 채점을 누가 했나. */
enum class Engine { CLI, MANUAL }

@Entity(
    tableName = "items",
    indices = [Index("createdAt"), Index("archived")]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 재현할 때 사용자가 보는 유일한 단서 */
    val title: String,
    val body: String,
    val kind: ItemKind = ItemKind.LIST,
    val tags: String = "",
    /** 지금 살아 있는 리비전. 회차는 자기 리비전을 따로 붙든다. */
    val currentRevisionId: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
    /** 5회차를 모두 끝냈거나 사용자가 접은 항목 */
    val archived: Boolean = false
)

/**
 * 원본의 불변 스냅샷.
 *
 * 원본을 고쳐도 과거 채점이 깨지면 안 된다. 그래서 items 는 갱신하되 여기에 한 줄을
 * 새로 쌓고, 회차는 자기가 발화될 때의 리비전 id 를 붙든다. 이 표의 행은 절대
 * UPDATE / DELETE 하지 않는다.
 */
@Entity(
    tableName = "item_revisions",
    foreignKeys = [ForeignKey(
        entity = ItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["itemId", "revision"], unique = true)]
)
data class RevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val revision: Int,
    val title: String,
    val body: String,
    val kind: ItemKind,
    /** 이 리비전의 채점 기준 — ElementSheet 의 JSON. 회차가 이걸 붙들고 채점한다. */
    val sheetJson: String = "",
    /** CLI 정규화를 거쳤는가. false 면 줄 단위로 대충 자른 임시 요소표다. */
    val normalized: Boolean = false,
    val createdAt: Long
)

/**
 * 한 번의 인출 회차.
 *
 * [roundIndex] 0..4 가 D0 / D+3 / D+10 / D+30 / D+90 이고, red 를 받으면 같은
 * roundIndex 에 [attempt] 만 올린 재시도 행이 다음 날짜로 하나 더 생긴다.
 * 재시도가 통과하면 다음 회차는 원래 캘린더 날짜 그대로 간다.
 */
@Entity(
    tableName = "rounds",
    foreignKeys = [ForeignKey(
        entity = ItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("itemId"), Index("dueAt"), Index("state"),
        Index(value = ["itemId", "roundIndex", "attempt"], unique = true)
    ]
)
data class RoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val roundIndex: Int,
    val attempt: Int = 0,
    val offsetDays: Int,
    val dueAt: Long,
    /** 알림이 나가는 순간 확정된다. 그 전엔 0 (= 현재 리비전을 따라감) */
    val revisionId: Long = 0,
    val state: RoundState = RoundState.PENDING,
    val notifiedAt: Long? = null,
    val completedAt: Long? = null
)

@Entity(
    tableName = "submissions",
    foreignKeys = [ForeignKey(
        entity = RoundEntity::class,
        parentColumns = ["id"],
        childColumns = ["roundId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["roundId"], unique = true), Index("itemId")]
)
data class SubmissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val itemId: Long,
    val kind: SubmissionKind,
    val text: String = "",
    /** 종이를 찍은 사진의 절대 경로 (앱 전용 외부 저장소) */
    val imagePath: String? = null,
    val usedHint: Boolean = false,
    val elapsedMs: Long = 0,
    val submittedAt: Long
)

/**
 * 채점 결과 한 건. [rawText] 에 CLI 원응답을 통째로 남겨 파싱이 틀렸을 때
 * 다시 읽을 수 있게 한다.
 */
@Entity(
    tableName = "comparisons",
    foreignKeys = [ForeignKey(
        entity = SubmissionEntity::class,
        parentColumns = ["id"],
        childColumns = ["submissionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["submissionId"], unique = true)]
)
data class ComparisonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val submissionId: Long,
    val roundId: Long,
    /** 맞힌 요소 수 / 원본 요소 수 — 백분율은 저장하지 않는다 */
    val hit: Int = 0,
    val total: Int = 0,
    val grade: Grade = Grade.UNGRADED,
    /** 앱이 제안한 등급. 사용자가 뒤집으면 grade 만 바뀌고 이건 남는다. */
    val suggestedGrade: Grade = Grade.UNGRADED,
    val score: Int = 0,
    /** Judgement 리스트의 JSON */
    val judgementsJson: String = "[]",
    /** Extra 리스트의 JSON — 원본에 없는데 재현물에 있던 것 */
    val extrasJson: String = "[]",
    /** 사진을 읽어 옮겨 적은 글 (글로 제출했으면 빈 값) */
    val transcript: String = "",
    val summary: String = "",
    val advice: String = "",
    /** 원본 쪽이 틀린 것 같다고 채점기가 신고했는가 */
    val reviewOriginal: Boolean = false,
    val engine: Engine = Engine.CLI,
    val status: String = "PENDING", // PENDING / OK / FAILED / TIMEOUT
    val failReason: String? = null,
    val rawText: String = "",
    val createdAt: Long
)
