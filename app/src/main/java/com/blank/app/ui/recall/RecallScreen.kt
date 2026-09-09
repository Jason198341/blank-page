package com.blank.app.ui.recall

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blank.app.ui.common.RoundBadge
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.BgRecall
import com.blank.app.ui.theme.CardBg
import com.blank.app.ui.theme.Outline
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary
import com.blank.app.ui.theme.TextTertiary
import com.blank.app.util.Dates
import com.blank.app.util.PhotoFiles
import kotlinx.coroutines.delay
import java.io.File

/**
 * 재현 화면. 이 앱의 전부다.
 *
 * 원본은 제출 전까지 열리지 않는다 — 보고 그리는 건 연습이 아니기 때문이다.
 * 잠금은 경고색이 아니라 회색 약속조로 보여준다. 벌주는 느낌을 주면 사용자는
 * 앱 밖에서 원본을 찾아본다.
 */
@Composable
fun RecallScreen(
    roundId: Long,
    onSubmitted: (Long) -> Unit,
    onBack: () -> Unit,
    vm: RecallViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(roundId) { vm.load(roundId) }

    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.startedAt) {
        while (true) {
            elapsed = System.currentTimeMillis() - state.startedAt
            delay(1000)
        }
    }

    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        vm.onPhoto(if (ok) pendingPhoto?.absolutePath else null)
    }

    val hasContent = state.text.isNotBlank() || state.imagePath != null

    Column(
        Modifier
            .fillMaxSize()
            .background(BgRecall)
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = TextSecondary)
            }
            Spacer(Modifier.weight(1f))
            state.round?.let { RoundBadge(it.roundIndex, it.attempt) }
            Spacer(Modifier.width(10.dp))
            // 카운트업만. 제한시간은 인출을 망친다.
            Text(Dates.formatElapsed(elapsed), fontSize = 13.sp, color = TextTertiary)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(state.title, style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            LockCard(state.hintText)

            Spacer(Modifier.height(24.dp))
            Text(
                "종이에 다 쓰셨나요? 옮겨 적으세요.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::onTextChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                placeholder = { Text("한 줄에 하나씩", color = TextTertiary) },
                // 자동완성·예측이 힌트가 된다. 전부 끈다.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Outline,
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                )
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val file = PhotoFiles.newFile(context, roundId)
                    pendingPhoto = file
                    takePicture.launch(PhotoFiles.uriFor(context, file))
                }) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = TextSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state.imagePath == null) "종이 촬영" else "다시 촬영",
                        color = TextSecondary
                    )
                }
                if (state.hintAvailable && state.hintText == null) {
                    TextButton(onClick = vm::useHint) {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = TextSecondary)
                        Spacer(Modifier.width(6.dp))
                        Text("힌트 1회", color = TextSecondary)
                    }
                }
            }
            state.imagePath?.let {
                Text(
                    "사진 1장 첨부됨 · 글씨를 읽어 함께 채점합니다",
                    style = MaterialTheme.typography.labelMedium, color = TextTertiary
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(
                onClick = { vm.submit(gaveUp = !hasContent) { onSubmitted(it) } },
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasContent) Accent else CardBg,
                    contentColor = if (hasContent) Color.White else TextSecondary
                )
            ) {
                // 빈 입력이면 실패 버튼이 아니라 "종료 선언" 으로 읽히게 문구가 바뀐다
                Text(if (hasContent) "제출" else "여기까지가 전부입니다")
            }
        }
    }
}

@Composable
private fun LockCard(hint: String?) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(12.dp))
            .border(1.dp, Outline, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = TextTertiary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "원본은 제출한 뒤 열립니다.",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                )
            }
            if (hint != null) {
                Spacer(Modifier.height(10.dp))
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
    }
}
