package com.blank.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 오늘 화면·캘린더가 쓰는 조인 결과 */
data class DueRound(
    @Embedded val round: RoundEntity,
    val title: String,
    val tags: String,
    val kind: ItemKind,
    /** LEFT JOIN 이라 아직 채점이 없으면 null. enum 컨버터를 태우지 않고 날것으로 받는다. */
    val grade: String?,
    val hit: Int?,
    val total: Int?
) {
    fun gradeOrNull(): Grade? = grade?.let { runCatching { Grade.valueOf(it) }.getOrNull() }
}

private const val DUE_SELECT = """
    SELECT r.*, i.title AS title, i.tags AS tags, i.kind AS kind,
           c.grade AS grade, c.hit AS hit, c.total AS total
    FROM rounds r
    JOIN items i ON i.id = r.itemId
    LEFT JOIN submissions s ON s.roundId = r.id
    LEFT JOIN comparisons c ON c.submissionId = s.id
"""

@Dao
interface ItemDao {
    @Insert suspend fun insert(item: ItemEntity): Long
    @Update suspend fun update(item: ItemEntity)
    @Query("SELECT * FROM items WHERE id = :id") suspend fun byId(id: Long): ItemEntity?
    @Query("SELECT * FROM items WHERE id = :id") fun flowById(id: Long): Flow<ItemEntity?>
    @Query("SELECT * FROM items ORDER BY archived ASC, createdAt DESC") fun all(): Flow<List<ItemEntity>>
    @Query("SELECT COUNT(*) FROM items WHERE archived = 0") suspend fun activeCount(): Int
    @Query("UPDATE items SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)
    @Query("DELETE FROM items WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface RevisionDao {
    @Insert suspend fun insert(revision: RevisionEntity): Long
    @Query("SELECT * FROM item_revisions WHERE id = :id") suspend fun byId(id: Long): RevisionEntity?
    @Query("SELECT MAX(revision) FROM item_revisions WHERE itemId = :itemId") suspend fun maxRevision(itemId: Long): Int?
}

@Dao
interface RoundDao {
    @Insert suspend fun insertAll(rounds: List<RoundEntity>)
    @Insert suspend fun insert(round: RoundEntity): Long
    @Update suspend fun update(round: RoundEntity)

    @Query("SELECT * FROM rounds WHERE id = :id") suspend fun byId(id: Long): RoundEntity?

    /** 다음에 알람을 걸 단 하나의 회차. 알람은 이것 하나만 건다. */
    @Query("SELECT * FROM rounds WHERE state = 'PENDING' ORDER BY dueAt ASC LIMIT 1")
    suspend fun nextPending(): RoundEntity?

    /** 시각이 지났는데 아직 알림이 안 나간 것들 (안전망 워커용) */
    @Query("SELECT * FROM rounds WHERE state = 'PENDING' AND dueAt <= :now ORDER BY dueAt ASC")
    suspend fun overdue(now: Long): List<RoundEntity>

    @Query("SELECT * FROM rounds WHERE itemId = :itemId ORDER BY roundIndex ASC, attempt ASC")
    fun flowByItem(itemId: Long): Flow<List<RoundEntity>>

    @Query("SELECT * FROM rounds WHERE itemId = :itemId AND state != 'DONE' AND dueAt > :after ORDER BY dueAt ASC")
    suspend fun futureOf(itemId: Long, after: Long): List<RoundEntity>

    /** 오늘 화면: 시각이 지난 미완료 전부 (이월분 포함) */
    @Transaction
    @Query("$DUE_SELECT WHERE r.dueAt <= :until AND r.state != 'DONE' AND r.state != 'SKIPPED' ORDER BY r.dueAt ASC, r.roundIndex ASC")
    fun flowDueUntil(until: Long): Flow<List<DueRound>>

    /** 오늘 화면 하단: 오늘 이미 끝낸 것들 */
    @Transaction
    @Query("$DUE_SELECT WHERE r.completedAt BETWEEN :from AND :to ORDER BY r.completedAt DESC")
    fun flowCompletedBetween(from: Long, to: Long): Flow<List<DueRound>>

    /** 캘린더 한 달치 */
    @Transaction
    @Query("$DUE_SELECT WHERE r.dueAt BETWEEN :from AND :to ORDER BY r.dueAt ASC, r.roundIndex ASC")
    fun flowBetween(from: Long, to: Long): Flow<List<DueRound>>

    @Query("SELECT COUNT(*) FROM rounds WHERE dueAt BETWEEN :from AND :to AND state != 'DONE'")
    suspend fun countDueBetween(from: Long, to: Long): Int

    @Query("DELETE FROM rounds WHERE itemId = :itemId AND state = 'PENDING'")
    suspend fun deletePendingOf(itemId: Long)
}

@Dao
interface SubmissionDao {
    @Insert suspend fun insert(submission: SubmissionEntity): Long
    @Query("SELECT * FROM submissions WHERE id = :id") suspend fun byId(id: Long): SubmissionEntity?
    @Query("SELECT * FROM submissions WHERE roundId = :roundId") suspend fun byRound(roundId: Long): SubmissionEntity?
}

@Dao
interface ComparisonDao {
    @Insert suspend fun insert(comparison: ComparisonEntity): Long
    @Update suspend fun update(comparison: ComparisonEntity)
    @Query("SELECT * FROM comparisons WHERE submissionId = :submissionId") suspend fun bySubmission(submissionId: Long): ComparisonEntity?
    @Query("SELECT * FROM comparisons WHERE submissionId = :submissionId") fun flowBySubmission(submissionId: Long): Flow<ComparisonEntity?>
    @Query("SELECT * FROM comparisons WHERE roundId = :roundId") fun flowByRound(roundId: Long): Flow<ComparisonEntity?>
    /** 채점이 아직 안 끝난 것들 — 앱을 다시 열었을 때 이어받는다 */
    @Query("SELECT * FROM comparisons WHERE status = 'PENDING'") suspend fun pending(): List<ComparisonEntity>
}
