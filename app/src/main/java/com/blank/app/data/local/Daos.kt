package com.blank.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
    val grade: String?,
    val hit: Int?,
    val total: Int?
) {
    fun gradeOrNull(): Grade? = grade?.let { runCatching { Grade.valueOf(it) }.getOrNull() }
}

private const val DUE_SELECT = """
    SELECT r.*, n.title AS title, n.tags AS tags, n.kind AS kind,
           c.grade AS grade, c.hit AS hit, c.total AS total
    FROM rounds r
    JOIN notes n ON n.id = r.noteId
    LEFT JOIN submissions s ON s.roundId = r.id
    LEFT JOIN comparisons c ON c.submissionId = s.id
"""

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(note: NoteEntity)
    @Update suspend fun update(note: NoteEntity)
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun byId(id: String): NoteEntity?
    @Query("SELECT * FROM notes WHERE id = :id") fun flowById(id: String): Flow<NoteEntity?>
    @Query("SELECT * FROM notes WHERE relPath = :relPath") suspend fun byPath(relPath: String): NoteEntity?
    @Query("SELECT * FROM notes WHERE missing = 0 ORDER BY updatedAt DESC") fun all(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE missing = 0") suspend fun allNow(): List<NoteEntity>
    @Query("SELECT COUNT(*) FROM notes WHERE referenceOnly = 0 AND archived = 0 AND missing = 0")
    suspend fun activeReviewCount(): Int
    /** 위키링크 해석용 제목→id */
    @Query("SELECT id, title FROM notes WHERE missing = 0")
    suspend fun titleIndex(): List<TitleRow>
    @Query("UPDATE notes SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE notes SET missing = :missing WHERE id = :id") suspend fun setMissing(id: String, missing: Boolean)
}

data class TitleRow(val id: String, val title: String)

@Dao
interface RevisionDao {
    @Insert suspend fun insert(revision: RevisionEntity): Long
    @Query("SELECT * FROM note_revisions WHERE id = :id") suspend fun byId(id: Long): RevisionEntity?
    @Query("SELECT MAX(revision) FROM note_revisions WHERE noteId = :noteId") suspend fun maxRevision(noteId: String): Int?
}

@Dao
interface RoundDao {
    @Insert suspend fun insertAll(rounds: List<RoundEntity>)
    @Insert suspend fun insert(round: RoundEntity): Long
    @Update suspend fun update(round: RoundEntity)

    @Query("SELECT * FROM rounds WHERE id = :id") suspend fun byId(id: Long): RoundEntity?
    @Query("SELECT COUNT(*) FROM rounds WHERE noteId = :noteId") suspend fun countForNote(noteId: String): Int

    @Query("SELECT * FROM rounds WHERE state = 'PENDING' ORDER BY dueAt ASC LIMIT 1")
    suspend fun nextPending(): RoundEntity?

    @Query("SELECT * FROM rounds WHERE state = 'PENDING' AND dueAt <= :now ORDER BY dueAt ASC")
    suspend fun overdue(now: Long): List<RoundEntity>

    @Query("SELECT * FROM rounds WHERE noteId = :noteId ORDER BY roundIndex ASC, attempt ASC")
    fun flowByNote(noteId: String): Flow<List<RoundEntity>>

    @Query("SELECT * FROM rounds WHERE noteId = :noteId AND state != 'DONE' AND dueAt > :after ORDER BY dueAt ASC")
    suspend fun futureOf(noteId: String, after: Long): List<RoundEntity>

    /** 참조용으로 바뀌거나 파일이 사라진 노트의 예정 회차를 접는다 */
    @Query("UPDATE rounds SET state = 'SKIPPED' WHERE noteId = :noteId AND state = 'PENDING'")
    suspend fun skipPendingOf(noteId: String)

    @Transaction
    @Query("$DUE_SELECT WHERE r.dueAt <= :until AND r.state != 'DONE' AND r.state != 'SKIPPED' ORDER BY r.dueAt ASC, r.roundIndex ASC")
    fun flowDueUntil(until: Long): Flow<List<DueRound>>

    @Transaction
    @Query("$DUE_SELECT WHERE r.completedAt BETWEEN :from AND :to ORDER BY r.completedAt DESC")
    fun flowCompletedBetween(from: Long, to: Long): Flow<List<DueRound>>

    @Transaction
    @Query("$DUE_SELECT WHERE r.dueAt BETWEEN :from AND :to AND r.state != 'SKIPPED' ORDER BY r.dueAt ASC, r.roundIndex ASC")
    fun flowBetween(from: Long, to: Long): Flow<List<DueRound>>
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
    @Query("SELECT * FROM comparisons WHERE status = 'PENDING'") suspend fun pending(): List<ComparisonEntity>
}
