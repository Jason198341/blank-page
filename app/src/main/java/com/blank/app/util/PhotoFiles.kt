package com.blank.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 재현물 사진은 앱 전용 외부 저장소에 둔다. 저장소 권한이 필요 없고,
 * Termux 의 claude 가 `--add-dir` 로 읽을 수 있는 실제 경로가 나온다.
 */
object PhotoFiles {

    fun newFile(context: Context, roundId: Long): File {
        val dir = File(context.getExternalFilesDir(null), "shots").apply { mkdirs() }
        return File(dir, "r${roundId}_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
