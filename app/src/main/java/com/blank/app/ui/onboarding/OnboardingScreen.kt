package com.blank.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.Bg
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary

@Composable
fun OnboardingScreen(onPickVault: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("백지", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
        Spacer(Modifier.height(16.dp))
        Text(
            "지식을 마크다운 노트로 쌓고, 인출로 각인합니다.\n" +
                "노트는 당신이 고른 폴더의 .md 파일로 저장돼\n옵시디언에서도 그대로 열립니다.",
            style = MaterialTheme.typography.bodyLarge, color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onPickVault,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
        ) { Text("볼트 폴더 고르기") }
        Spacer(Modifier.height(16.dp))
        Text(
            "빈 폴더를 새로 만들어 골라도 되고,\n기존 옵시디언 볼트를 골라도 됩니다.",
            style = MaterialTheme.typography.labelMedium, color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
