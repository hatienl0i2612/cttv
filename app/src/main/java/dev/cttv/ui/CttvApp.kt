package dev.cttv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text

private enum class Section { Television, Football }

@Composable
fun CttvApp(football: @Composable (() -> Unit) -> Unit = { FootballRoute(it) }, television: @Composable () -> Unit) {
    var section by rememberSaveable { mutableStateOf<Section?>(null) }
    var lastSection by rememberSaveable { mutableStateOf(Section.Television) }

    BackHandler(enabled = section != null) { section = null }

    when (section) {
        Section.Television -> television()
        Section.Football -> football { section = null }
        null -> HomeScreen(lastSection) { lastSection = it; section = it }
    }
}

@Composable
private fun HomeScreen(selected: Section, onSelect: (Section) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Background).padding(48.dp).testTag("home-screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("CTTV", color = Accent, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Chọn nội dung bạn muốn xem", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(32.dp))
        Row(
            Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            for ((section, title) in listOf(Section.Television to "1. Truyền hình", Section.Football to "2. Bóng đá")) {
                val focus = remember { FocusRequester() }
                Button(
                    onClick = { onSelect(section) },
                    modifier = Modifier.weight(1f).height(120.dp).focusRequester(focus)
                        .testTag("home-${section.name}")
                        .pointerInput(onSelect) {
                            detectTapGestures { focus.requestFocus(); onSelect(section) }
                        },
                    colors = ButtonDefaults.colors(
                        containerColor = Panel, contentColor = Muted,
                        focusedContainerColor = Accent, focusedContentColor = Background,
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = ButtonDefaults.scale(focusedScale = 1.04f),
                ) {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
                if (section == selected) LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
    }
}
