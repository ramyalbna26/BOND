package com.sanad.bond.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.model.RichTextState

@Composable
fun EditorRibbon(richTextState: RichTextState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // الصف الأول: تنسيق نص
            RibbonRow {
                RibbonToggle(
                    icon = Icons.Default.FormatBold,
                    active = richTextState.currentSpanStyle.fontWeight == FontWeight.Bold,
                    onClick = { richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }
                )
                RibbonToggle(
                    icon = Icons.Default.FormatItalic,
                    active = richTextState.currentSpanStyle.fontStyle == FontStyle.Italic,
                    onClick = { richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }
                )
                RibbonToggle(
                    icon = Icons.Default.FormatUnderlined,
                    active = richTextState.currentSpanStyle.textDecoration == TextDecoration.Underline,
                    onClick = { richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }
                )
                RibbonToggle(
                    icon = Icons.Default.FormatStrikethrough,
                    active = richTextState.currentSpanStyle.textDecoration == TextDecoration.LineThrough,
                    onClick = { richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) }
                )
                RibbonDivider()
                RibbonButton(
                    icon = Icons.Default.FormatColorText,
                    onClick = {
                        val current = richTextState.currentSpanStyle.color
                        val newColor = if (current == Color.Black) Color.Red else Color.Black
                        richTextState.toggleSpanStyle(SpanStyle(color = newColor))
                    }
                )
                RibbonButton(
                    icon = Icons.Default.Highlight,
                    onClick = {
                        val current = richTextState.currentSpanStyle.background
                        val newBg = if (current == Color.Yellow) Color.Transparent else Color.Yellow
                        richTextState.toggleSpanStyle(SpanStyle(background = newBg))
                    }
                )
            }

            // الصف الثاني: محاذاة + قوائم
            RibbonRow {
                RibbonButton(
                    icon = Icons.AutoMirrored.Filled.FormatAlignLeft,
                    onClick = { richTextState.toggleParagraphStyle(ParagraphStyle(textAlign = TextAlign.Left)) }
                )
                RibbonButton(
                    icon = Icons.Default.FormatAlignCenter,
                    onClick = { richTextState.toggleParagraphStyle(ParagraphStyle(textAlign = TextAlign.Center)) }
                )
                RibbonButton(
                    icon = Icons.AutoMirrored.Filled.FormatAlignRight,
                    onClick = { richTextState.toggleParagraphStyle(ParagraphStyle(textAlign = TextAlign.Right)) }
                )
                RibbonButton(
                    icon = Icons.Default.FormatAlignJustify,
                    onClick = { richTextState.toggleParagraphStyle(ParagraphStyle(textAlign = TextAlign.Justify)) }
                )
                RibbonDivider()
                RibbonButton(
                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    onClick = { richTextState.toggleUnorderedList() }
                )
                RibbonButton(
                    icon = Icons.Default.FormatListNumbered,
                    onClick = { richTextState.toggleOrderedList() }
                )
            }

            // الصف الثالث: عناوين وأحجام
            RibbonRow {
                TextChip(label = "H1", onClick = {
                    richTextState.toggleSpanStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold))
                })
                TextChip(label = "H2", onClick = {
                    richTextState.toggleSpanStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold))
                })
                TextChip(label = "H3", onClick = {
                    richTextState.toggleSpanStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold))
                })
                RibbonDivider()
                TextChip(label = "A-", onClick = {
                    richTextState.toggleSpanStyle(SpanStyle(fontSize = 14.sp))
                })
                TextChip(label = "A+", onClick = {
                    richTextState.toggleSpanStyle(SpanStyle(fontSize = 20.sp))
                })
            }
        }
    }
}

@Composable
private fun RibbonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
private fun RibbonToggle(
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Surface(
            color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            shape = RoundedCornerShape(6.dp)
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RibbonButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun RibbonDivider() {
    Box(
        modifier = Modifier
            .height(24.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 4.dp)
    )
}

@Composable
private fun TextChip(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}