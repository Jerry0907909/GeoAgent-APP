package com.geoagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.White
import java.io.File

@Composable
fun UserAvatar(
    displayName: String?,
    remoteUrl: String?,
    localUri: String?,
    modifier: Modifier = Modifier,
    sizeDp: Int = 48
) {
    val imageModel = resolveAvatarModel(localUri, remoteUrl)

    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(BrandPrimary),
        contentAlignment = Alignment.Center
    ) {
        when {
            imageModel != null -> {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier
                        .size(sizeDp.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            !displayName.isNullOrBlank() -> {
                Text(
                    text = displayName.take(1).uppercase(),
                    color = White,
                    fontSize = (sizeDp / 2.4).sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            else -> {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = White,
                    modifier = Modifier.size((sizeDp * 0.5f).dp)
                )
            }
        }
    }
}

private fun resolveAvatarModel(localUri: String?, remoteUrl: String?): Any? {
    if (!localUri.isNullOrBlank() && !localUri.startsWith("preset:")) {
        return when {
            localUri.startsWith("content:") || localUri.startsWith("file:") -> localUri
            else -> File(localUri)
        }
    }
    if (!remoteUrl.isNullOrBlank()) return remoteUrl
    return null
}
