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

enum class RoundState { PENDING, NOTIFIED, DONE, SKIPPED }

enum class Grade { GREEN, AMBER, RED, UNGRADED }

enum class SubmissionKind { TEXT, IMAGE, BOTH, GAVE_UP }

enum class Engine { CLI, MANUAL }

/**
 * 노트 = 볼트의 .md 파일 하나. **파일이 진실이고 이 행은 색인**이다.
 *
 * 정체성은 [id] (프론트매터 blank_id, uuid). 옵시디언에서 파일 이름·위치를 바꿔도
 * 이 id 로 복습 이력을 잇는다. [body] 는 파일에서 읽어 캐시한 것 — 화면 표시와 CLI
 * 정규화에 쓰고, 파일이 바뀌면 다시 채운다.
 *
 * [referenceOnly] = true 면 단순 참조용이라 복습 스케줄에서 뺀다. 프론트매터의
 * `review: false` 가 이 값이다.
 */
@Entity(
    tableName = "notes",
    indices = [Index("relPath"), Index("title"), Index("referenceOnly"), Index("updatedAt")]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    /** 볼트 루트 기준 상대 경로 (예: "물리/맥스웰.md") */
    val relPath: String,
    /** 표시 제목. 파일명(확장자 뺀) 또는 첫 # 제목 */
    val title: String,
    /** 프론트매터를 뺀 마크다운 본문 (파일에서 읽어 캐시) */
    val body: String,
    val kind: ItemKind = ItemKind.LIST,
    val tags: String = "",
    val referenceOnly: Boolean = false,
    /** 채점 기준 요소표(ElementSheet)의 JSON. 복습 노트만 채워진다. */
    val sheetJson: String = "",
    val normalized: Boolean = false,
    /** 이 노트가 [[링크]]로 가리키는 제목들의 JSON 배열 */
    val linkTitlesJson: String = "[]",
    /** 5회차를 한 번이라도 깔았는가 (동기화마다 다시 깔지 않기 위해) */
    val scheduled: Boolean = false,
    /** 파일이 사라졌는가 (복습 이력은 남기되 목록에서 흐리게) */
    val missing: Boolean = false,
    /** 5회차를 마쳐 아카이브됐는가 */
    val archived: Boolean = false,
    /** 마지막으로 색인한 파일 수정 시각·내용 해시 (재파싱 여부 판단) */
    val fileMtime: Long = 0,
    val contentHash: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 회차가 발화될 때의 원본 스냅샷. 파일이 진실이라 옵시디언에서든 앱에서든 나중에
 * 원본이 바뀔 수 있는데, 알림이 나간 뒤엔 그 회차의 채점 기준이 고정돼야 한다.
 * 그래서 알림 시점의 본문·요소표를 여기 얼려 둔다. 이 행은 UPDATE/DELETE 하지 않는다.
 */
@Entity(
    tableName = "note_revisions",
    indices = [Index(value = ["noteId", "revision"], unique = true)]
)
data class RevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: String,
    val revision: Int,
    val title: String,
    val body: String,
    val kind: ItemKind,
    val sheetJson: String,
    val createdAt: Long
)

@Entity(
    tableName = "rounds",
    indices = [
        Index("noteId"), Index("dueAt"), Index("state"),
        Index(value = ["noteId", "roundIndex", "attempt"], unique = true)
    ]
)
data class RoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: String,
    val roundIndex: Int,
    val attempt: Int = 0,
    val offsetDays: Int,
    val dueAt: Long,
    /** 알림이 나가는 순간 확정되는 스냅샷 리비전 id. 그 전엔 0. */
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
    indices = [Index(value = ["roundId"], unique = true), Index("noteId")]
)
data class SubmissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roundId: Long,
    val noteId: String,
    val kind: SubmissionKind,
    val text: String = "",
    val imagePath: String? = null,
    val usedHint: Boolean = false,
    val elapsedMs: Long = 0,
    val submittedAt: Long
)

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
    val hit: Int = 0,
    val total: Int = 0,
    val score: Int = 0,
    val grade: Grade = Grade.UNGRADED,
    val suggestedGrade: Grade = Grade.UNGRADED,
    val judgementsJson: String = "[]",
    val extrasJson: String = "[]",
    val transcript: String = "",
    val summary: String = "",
    val advice: String = "",
    val reviewOriginal: Boolean = false,
    val engine: Engine = Engine.CLI,
    val status: String = "PENDING",
    val failReason: String? = null,
    val rawText: String = "",
    val createdAt: Long
)
