package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val Icons.AutoMirrored.Filled.ArrowForwardIos: ImageVector by lazy {
    materialIcon(name = "AutoMirrored.Filled.ArrowForwardIos", autoMirror = true) {
        materialPath {
            moveTo(6.23f, 20.23f)
            lineTo(8f, 22f)
            lineToRelative(10f, -10f)
            lineTo(8f, 2f)
            lineTo(6.23f, 3.77f)
            lineTo(14.46f, 12f)
            close()
        }
    }
}
