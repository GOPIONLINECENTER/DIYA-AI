package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppLanguage
import com.example.model.ChatMessage
import com.example.model.DiyaCopy
import com.example.model.SenderRole
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.ObsidianPanel
import com.example.ui.theme.StandbyAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.holoGlassPanel

@Composable
fun ChatConversationPanel(
    messages: List<ChatMessage>,
    draftText: String,
    language: AppLanguage,
    onDraftChanged: (String) -> Unit,
    onSendMessage: (String?) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .holoGlassPanel(shape = RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DIYA CONVERSATION",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Text(
                    text = "BN • EN • HI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary
                    )
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(message = msg)
            }
        }

        // Quick multilingual prompt chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiyaCopy.quickPrompts(language).forEachIndexed { idx, (langTag, prompt) ->
                val interactionSource = remember { MutableInteractionSource() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .background(GlassSurfaceElevated.copy(alpha = 0.65f))
                        .border(0.8.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true, color = ElectricCyan),
                            onClick = { onSendMessage(prompt) }
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("quick_prompt_$idx")
                ) {
                    Text(
                        text = langTag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                    )
                }
            }
        }

        // Composer Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draftText,
                onValueChange = onDraftChanged,
                placeholder = {
                    Text(
                        text = when (language) {
                            AppLanguage.BENGALI -> "বাংলা, ইংরেজি বা হিন্দিতে লিখুন (UI প্রিভিউ)..."
                            AppLanguage.ENGLISH -> "Type in Bengali, English, or Hindi (Preview)..."
                            AppLanguage.HINDI -> "बंगाली, अंग्रेज़ी या हिन्दी में लिखें (प्रीव्यू)..."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendMessage(null) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = ElectricBlue.copy(alpha = 0.38f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = ElectricCyan,
                    focusedContainerColor = ObsidianPanel.copy(alpha = 0.72f),
                    unfocusedContainerColor = ObsidianPanel.copy(alpha = 0.52f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field")
            )

            IconButton(
                onClick = { onSendMessage(null) },
                modifier = Modifier
                    .size(50.dp)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ElectricCyan, ElectricBlue)
                        )
                    )
                    .testTag("send_message_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.cd_send_message),
                    tint = DeepSpaceBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    when (message.role) {
        SenderRole.SYSTEM_NOTE -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StandbyAmber.copy(alpha = 0.09f))
                    .border(0.8.dp, StandbyAmber.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = StandbyAmber.copy(alpha = 0.95f)
                    )
                )
            }
        }
        SenderRole.USER -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    ElectricBlue.copy(alpha = 0.45f),
                                    ElectricCyan.copy(alpha = 0.25f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            ElectricCyan.copy(alpha = 0.45f),
                            RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${message.languageTag} • ${message.timestamp}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontSize = 9.5.sp
                        )
                    )
                }
            }
        }
        SenderRole.DIYA -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                        .background(GlassSurface.copy(alpha = 0.85f))
                        .border(
                            1.dp,
                            ElectricCyan.copy(alpha = 0.3f),
                            RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "DIYA AI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (message.isPreviewTag) {
                            Text(
                                text = "UI PREVIEW",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = StandbyAmber,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${message.languageTag} • ${message.timestamp}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextMuted,
                            fontSize = 9.5.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    draftText: String,
    language: AppLanguage,
    onDraftChanged: (String) -> Unit,
    onSendMessage: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        ChatConversationPanel(
            messages = messages,
            draftText = draftText,
            language = language,
            onDraftChanged = onDraftChanged,
            onSendMessage = onSendMessage,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 820.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}
