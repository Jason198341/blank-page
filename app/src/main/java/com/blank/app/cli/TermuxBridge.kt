package com.blank.app.cli

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 앱 ↔ 터미널(Termux 의 claude CLI) 브릿지.
 *
 *  - 앱 → 터미널 : Download/blank/req_*.txt 파일 + Termux RUN_COMMAND
 *  - 터미널 → 앱 : `am broadcast` → [CommandReceiver]
 *
 * 회신을 파일로 받지 않는 이유: Android 10+ 의 범위 지정 저장소에서 앱은 자기가 만들지
 * 않은 파일을 못 읽는다. 터미널이 결과 파일을 써 봐야 앱이 열 수 없다. 그래서 요청만
 * 파일로 보내고 결과는 브로드캐스트로 되받는다.
 *
 * 결과 JSON 이 binder 트랜잭션 한도에 걸리지 않도록 CLI 가 조각내 보내며,
 * 여기서 part/parts 를 보고 다시 붙인다.
 */
object TermuxBridge {

    const val PKG = "com.termux"
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val HOME = "/data/data/com.termux/files/home"
    /** repo 의 tools/blank 를 ~/bin/blank 로 링크해둔다 */
    private const val SCRIPT = "$HOME/bin/blank"
    private const val SUBDIR = "blank"

    // ------------------------------------------------------------------ 터미널 → 앱

    sealed class Event {
        data class Status(val req: String, val text: String) : Event()
        data class Done(val req: String, val json: String) : Event()
        data class Failed(val req: String, val reason: String) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun emit(event: Event) { _events.tryEmit(event) }

    /** 조각난 결과를 요청 id 별로 모은다 */
    private val chunks = mutableMapOf<String, Array<String?>>()

    @Synchronized
    fun collect(req: String, part: Int, parts: Int, json: String): String? {
        val slots = chunks.getOrPut(req) { arrayOfNulls(parts) }
        if (part !in slots.indices) return null
        slots[part] = json
        if (slots.any { it == null }) return null
        chunks.remove(req)
        return slots.joinToString("") { it.orEmpty() }
    }

    // ------------------------------------------------------------------ Termux 상태

    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PKG, 0) }.isSuccess

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun readiness(context: Context): String? = when {
        !isInstalled(context) -> "Termux 가 설치돼 있지 않습니다."
        !hasPermission(context) -> "Termux 명령 실행 권한이 없습니다 (앱 설정 → 권한)."
        else -> null
    }

    // ------------------------------------------------------------------ 앱 → 터미널

    data class Dispatch(val req: String, val path: String, val viaRunCommand: Boolean, val note: String?)

    /** 등록한 지식을 채점 가능한 요소표로 정규화해 달라고 맡긴다. */
    fun requestNormalize(
        context: Context,
        itemId: Long,
        title: String,
        body: String,
        kind: String
    ): Result<Dispatch> =
        send(context, op = "normalize", req = "n${itemId}_${System.currentTimeMillis()}",
            top = title.replace('\n', ' '), bottom = body, imagePath = null, kind = kind)

    /** 재현물을 요소표와 대조해 채점해 달라고 맡긴다. */
    fun requestGrade(
        context: Context,
        submissionId: Long,
        sheetJson: String,
        recall: String,
        imagePath: String?
    ): Result<Dispatch> =
        send(context, op = "grade", req = "g${submissionId}_${System.currentTimeMillis()}",
            top = sheetJson.replace("\n", " "), bottom = recall, imagePath = imagePath, kind = "")

    private fun send(
        context: Context,
        op: String,
        req: String,
        top: String,
        bottom: String,
        imagePath: String?,
        kind: String
    ): Result<Dispatch> {
        val body = buildString {
            append("op=").append(op).append('\n')
            append("req=").append(req).append('\n')
            append("kind=").append(kind).append('\n')
            append("model=claude-opus-5\n")
            append("image=").append(imagePath.orEmpty()).append('\n')
            append("---\n")
            append(top).append('\n')
            append("===\n")
            append(bottom)
            if (!bottom.endsWith("\n")) append('\n')
        }
        val path = writeNewFile(context, "req_$req.txt", body)
            ?: return Result.failure(IllegalStateException("요청 파일을 만들지 못했습니다 (Download/blank)"))

        val note = readiness(context)
        var via = false
        if (note == null) {
            val intent = Intent().apply {
                setClassName(PKG, "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", SCRIPT)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("run", path))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            via = runCatching { context.startService(intent) }.isSuccess
        }
        return Result.success(
            Dispatch(
                req = req,
                path = path,
                viaRunCommand = via,
                // RUN_COMMAND 가 막혀도 파일은 남는다 — 터미널에서 `blank drain` 하면 주워간다
                note = note ?: if (via) null else "Termux 서비스를 깨우지 못했습니다. 터미널에서 `blank drain`."
            )
        )
    }

    // ------------------------------------------------------------------ MediaStore 쓰기

    private fun writeNewFile(context: Context, name: String, body: String): String? {
        val resolver = context.contentResolver
        val uri = insertFile(context, name) ?: return null
        val ok = runCatching {
            resolver.openOutputStream(uri, "wt")?.use { it.write(body.toByteArray()) } != null
        }.getOrDefault(false)
        if (!ok) return null
        // 이름이 충돌하면 MediaStore 가 "(1)" 을 붙인다. 실제 이름을 되읽어야 경로가 맞는다.
        var display = name
        var rel = "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR/"
        runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { display = it }
                    c.getString(1)?.let { rel = it }
                }
            }
        }
        if (!rel.endsWith("/")) rel += "/"
        return Environment.getExternalStorageDirectory().absolutePath + "/" + rel + display
    }

    private fun insertFile(context: Context, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
        }
        return runCatching {
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull()
    }

    @Suppress("unused")
    private fun findOwnFile(context: Context, name: String): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        return runCatching {
            context.contentResolver.query(
                collection, arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
                arrayOf(name, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR/"), null
            )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }
        }.getOrNull()
    }
}
