package com.blank.app.data.vault

import android.content.Context
import com.blank.app.data.local.AppDatabase
import com.blank.app.data.local.NoteEntity
import com.blank.app.data.local.RoundState
import com.blank.app.data.prefs.SettingsStore
import com.blank.app.domain.BlankJson
import com.blank.app.domain.ElementSheet
import com.blank.app.domain.ReviewSchedule
import com.blank.app.notify.ScheduleSyncer
import kotlinx.serialization.encodeToString

/**
 * 볼트(.md 파일) → Room 색인 동기화. 파일이 진실이고 색인은 그 그림자다.
 *
 * 앱을 열 때, 폴더를 처음 고를 때, 옵시디언에서 편집한 뒤 돌아왔을 때 이걸 돌린다.
 * 하는 일:
 *  - 파일마다 프론트매터를 읽어 노트로 색인. blank_id 없으면 붙여 파일에 되쓴다.
 *  - review:true 인데 회차가 하나도 없으면 5회차를 깐다(첫 발견 시 한 번).
 *  - review:false(참조용)로 바뀌면 예정 회차를 접는다.
 *  - 파일이 사라진 노트는 missing 표시(이력은 남긴다).
 *  - 내용이 그대로면(해시 동일) 건너뛴다.
 */
object VaultSync {

    /** 사용자가 새 노트를 만들 때 CLI 정규화를 걸기 위한 콜백. Repository 가 채운다. */
    var onNeedsNormalize: (suspend (noteId: String, title: String, body: String, kind: String) -> Unit)? = null

    suspend fun sync(context: Context): Int {
        val db = AppDatabase.get(context)
        val settings = SettingsStore(context).get()
        val now = System.currentTimeMillis()
        val files = VaultStore.listMarkdown(context)
        val seen = HashSet<String>()
        var changed = 0

        for (vf in files) {
            val existing = db.notes().byPath(vf.relPath)
            // 내용 안 바뀐 파일은 건너뛴다
            if (existing != null && !existing.missing &&
                existing.fileMtime == vf.lastModified && existing.relPath == vf.relPath
            ) { seen += existing.id; continue }

            val raw = VaultStore.read(context, vf.uri)
            val hash = raw.hashCode().toString()
            if (existing != null && existing.contentHash == hash && !existing.missing) {
                db.notes().update(existing.copy(fileMtime = vf.lastModified))
                seen += existing.id; continue
            }

            // 이미 아는 노트면 그때 정한 기본값을 흔들지 않게, 신규만 설정 기본값을 따른다
            val defaultReview = existing?.let { !it.referenceOnly } ?: settings.enrollNewVaultNotes
            val parsed = Frontmatter.parse(raw, defaultReview, now)

            // blank_id 를 새로 붙였으면 파일에 되쓴다 (다음부터 이 id 로 추적)
            if (parsed.needsWriteback) {
                val rewritten = Frontmatter.serialize(
                    parsed.blankId, parsed.review, parsed.tags, parsed.kind, parsed.created, parsed.body
                )
                VaultStore.write(context, vf.uri, rewritten)
            }

            val title = titleOf(parsed.body, vf.name)
            val prior = db.notes().byId(parsed.blankId)
            val note = (prior ?: NoteEntity(
                id = parsed.blankId, relPath = vf.relPath, title = title, body = parsed.body,
                kind = parsed.kind, createdAt = parsed.created, updatedAt = now
            )).copy(
                relPath = vf.relPath,
                title = title,
                body = parsed.body,
                kind = parsed.kind,
                tags = parsed.tags.joinToString(","),
                referenceOnly = !parsed.review,
                linkTitlesJson = BlankJson.encodeToString(Frontmatter.linkTitles(parsed.body)),
                missing = false,
                fileMtime = vf.lastModified,
                contentHash = hash,
                updatedAt = now
            )
            db.notes().upsert(note)
            seen += note.id
            changed++

            reconcileSchedule(context, note, now)

            // 복습 노트인데 아직 요소표가 없으면 CLI 정규화를 건다
            if (parsed.review && !note.normalized && note.body.isNotBlank()) {
                onNeedsNormalize?.invoke(note.id, note.title, note.body, note.kind.name.lowercase())
            }
        }

        // 파일이 사라진 노트: missing 표시하고 예정 회차를 접는다
        db.notes().allNow().forEach { note ->
            if (note.id !in seen) {
                db.notes().setMissing(note.id, true)
                db.rounds().skipPendingOf(note.id)
                changed++
            }
        }

        ScheduleSyncer.resync(context)
        return changed
    }

    /** review 플래그와 회차 상태를 맞춘다 */
    private suspend fun reconcileSchedule(context: Context, note: NoteEntity, now: Long) {
        val db = AppDatabase.get(context)
        if (note.referenceOnly || note.archived) {
            // 참조용/아카이브: 예정 회차 접기
            db.rounds().skipPendingOf(note.id)
            return
        }
        // 복습 노트인데 회차가 하나도 없으면 첫 발견 — 5회차를 깐다
        if (db.rounds().countForNote(note.id) == 0) {
            db.rounds().insertAll(ReviewSchedule.plan(note.id, now, SettingsStore(context).get().notifyHour))
            db.notes().update(note.copy(scheduled = true))
        }
    }

    private fun titleOf(body: String, fileName: String): String {
        body.lineSequence().firstOrNull { it.trimStart().startsWith("#") }
            ?.let { return it.trimStart().trimStart('#').trim() }
        return fileName.removeSuffix(".md")
    }
}
