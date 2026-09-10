package com.blank.app.data.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * 볼트 = 사용자가 SAF 로 고른 폴더. **그 폴더의 .md 파일들이 진실**이고 앱은 색인만 든다.
 *
 * 트리 URI 를 영구 권한으로 붙들어 두고, 그 아래 .md 를 DocumentFile 로 읽고 쓴다.
 * 옵시디언이 같은 폴더를 열면 양쪽이 같은 파일을 본다.
 */
object VaultStore {

    private const val PREFS = "blank_vault"
    private const val KEY_TREE = "tree_uri"

    data class VaultFile(val relPath: String, val name: String, val uri: Uri, val lastModified: Long)

    fun treeUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TREE, null)?.let(Uri::parse)

    fun hasVault(context: Context): Boolean = treeUri(context)?.let { uri ->
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    } ?: false

    /** 폴더 선택 결과를 영구 권한으로 저장한다 */
    fun remember(context: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TREE, uri.toString()).apply()
    }

    /** 사람이 읽을 볼트 경로 (예: primary:Documents/blank-vault) */
    fun displayPath(context: Context): String? = treeUri(context)?.let {
        runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull()
    }

    private fun root(context: Context): DocumentFile? =
        treeUri(context)?.let { DocumentFile.fromTreeUri(context, it) }

    /** 볼트 전체를 훑어 .md 파일 목록을 낸다 (하위 폴더 포함) */
    fun listMarkdown(context: Context): List<VaultFile> {
        val root = root(context) ?: return emptyList()
        val out = mutableListOf<VaultFile>()
        fun walk(dir: DocumentFile, prefix: String) {
            dir.listFiles().forEach { f ->
                val name = f.name ?: return@forEach
                if (f.isDirectory) {
                    if (!name.startsWith(".")) walk(f, "$prefix$name/")
                } else if (name.endsWith(".md", true)) {
                    out += VaultFile("$prefix$name", name, f.uri, f.lastModified())
                }
            }
        }
        walk(root, "")
        return out
    }

    fun read(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull().orEmpty()

    fun write(context: Context, uri: Uri, content: String): Boolean =
        runCatching {
            // "wt" 로 열어 기존 내용을 자른다 (안 그러면 짧아진 파일에 옛 꼬리가 남는다)
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray()) }
            true
        }.getOrDefault(false)

    /**
     * 볼트에 새 .md 파일을 만든다. 하위 폴더 경로(relDir)가 있으면 따라 만든다.
     * 이름이 겹치면 SAF 가 "(1)" 을 붙이므로 실제 이름을 되읽어 상대경로를 돌려준다.
     */
    fun create(context: Context, relDir: String, fileName: String, content: String): VaultFile? {
        var dir = root(context) ?: return null
        var prefix = ""
        relDir.split("/").map { it.trim() }.filter { it.isNotEmpty() }.forEach { seg ->
            dir = dir.findFile(seg)?.takeIf { it.isDirectory } ?: dir.createDirectory(seg) ?: return null
            prefix += "$seg/"
        }
        val safeName = fileName.removeSuffix(".md")
        val file = dir.createFile("text/markdown", safeName) ?: return null
        write(context, file.uri, content)
        val actual = file.name ?: "$safeName.md"
        return VaultFile("$prefix$actual", actual, file.uri, file.lastModified())
    }

    fun delete(context: Context, relPath: String): Boolean {
        val f = findByPath(context, relPath) ?: return false
        return f.delete()
    }

    fun findByPath(context: Context, relPath: String): DocumentFile? {
        var dir = root(context) ?: return null
        val parts = relPath.split("/").filter { it.isNotEmpty() }
        parts.dropLast(1).forEach { seg ->
            dir = dir.findFile(seg)?.takeIf { it.isDirectory } ?: return null
        }
        return parts.lastOrNull()?.let { dir.findFile(it) }
    }
}
