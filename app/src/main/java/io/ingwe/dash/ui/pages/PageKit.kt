package io.ingwe.dash.ui.pages

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ingwe.dash.ui.theme.*

/** Text on the Economy / Air / Engine pages runs one step larger than the base labels. */
val PageLabel = LabelStyle.copy(fontSize = 14.sp)
val PageSmall = LabelStyle.copy(fontSize = 13.sp, fontFeatureSettings = "tnum")
val PageValue = ReadoutMedium.copy(fontSize = 26.sp)
val PageValueSmall = ReadoutSmall.copy(fontSize = 18.sp)

/** Where a reading comes from: "OBD-II" (standard) or "VW UDS · unconfirmed". */
@Composable
fun SourceTag(text: String) {
    Text(
        text = text,
        style = LabelStyle.copy(fontSize = 12.sp),
        color = IngweTextTertiary,
        modifier = Modifier
            .border(1.dp, IngweHairline, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Measure a small canvas label (call inside drawWithCache so it's measured once). */
fun TextMeasurer.label(text: String, color: Color, size: Float = 13f, weight: FontWeight = FontWeight.Medium): TextLayoutResult =
    measure(text, TextStyle(color = color, fontSize = size.sp, fontWeight = weight, fontFeatureSettings = "tnum"))

/** Draw a measured label anchored by fractions of its size (0.5f, 0.5f = centred on [at]). */
fun DrawScope.drawLabel(layout: TextLayoutResult, at: Offset, ax: Float = 0.5f, ay: Float = 0.5f) {
    drawText(layout, topLeft = Offset(at.x - layout.size.width * ax, at.y - layout.size.height * ay))
}
