package org.headfi.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.headfi.app.R

@Composable
fun HeadFiMonochromeLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_launcher_monochrome),
        contentDescription = "Head-Fi Logo",
        tint = tint,
        modifier = modifier.size(size)
    )
}
