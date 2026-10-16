package com.Hrshd1eux.filemanager.feature.viewer.unified.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Hrshd1eux.filemanager.core.storage.util.FileFormatter
import java.io.File

/**
 * Text and code preview content with line numbers, monospace toggle, and truncation guard.
 */
@Composable
fun TextPreviewContent(
    file: File,
    lines: List<String>,
    isTruncated: Boolean,
    totalLines: Int,
    totalBytes: Long,
    showLineNumbers: Boolean,
    wordWrap: Boolean,
    isMonospace: Boolean,
    modifier: Modifier = Modifier
) {
    val hScrollState = rememberScrollState()
    val fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isTruncated) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Showing first ${lines.size} of $totalLines lines (${FileFormatter.formatFileSize(totalBytes)}). Large file truncated for preview performance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        val contentModifier = if (!wordWrap) {
            Modifier
                .fillMaxSize()
                .horizontalScroll(hScrollState)
        } else {
            Modifier.fillMaxSize()
        }

        Box(modifier = contentModifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                itemsIndexed(lines, key = { index, _ -> index }) { index, lineText ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (showLineNumbers) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .width(42.dp)
                                    .padding(end = 10.dp)
                            )
                        }

                        Text(
                            text = if (lineText.isEmpty()) " " else lineText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = fontFamily,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}
