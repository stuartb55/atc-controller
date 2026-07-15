package com.stuart.atccontroller.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stuart.atccontroller.R
import java.text.NumberFormat

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.atcColors
    val backDescription = stringResource(R.string.cd_back)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = backDescription
                    }
                    .clickable(onClick = onBack),
                shape = CircleShape,
                color = colors.panelRaised,
                border = BorderStroke(1.dp, colors.line),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 22.sp, color = colors.green)
                }
            }
            Spacer(Modifier.width(14.dp))
        } else {
            RadarMark(38.dp)
            Spacer(Modifier.width(12.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.green,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun RadarMark(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.atcColors
    val description = stringResource(R.string.cd_radar_mark)
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(colors.green.copy(alpha = .12f), radius = this.size.minDimension / 2f)
        drawCircle(
            colors.green,
            radius = this.size.minDimension * .34f,
            style = Stroke(this.size.minDimension * .035f),
        )
        drawCircle(colors.green, radius = this.size.minDimension * .055f)
        drawLine(colors.green, center, Offset(this.size.width * .78f, this.size.height * .22f), 2f)
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    suffix: String = "→",
) {
    val colors = MaterialTheme.atcColors
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.green,
            contentColor = colors.night,
            disabledContainerColor = colors.panelRaised,
            disabledContentColor = colors.muted,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        Text(suffix, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.atcColors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DataPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val colors = MaterialTheme.atcColors
    val resolvedAccent = accent ?: colors.green
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = colors.panelRaised.copy(alpha = .88f),
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(resolvedAccent, CircleShape))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.muted)
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.atcColors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(18.dp).height(2.dp).background(colors.green))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.muted)
    }
}

@Composable
fun StarRating(stars: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = MaterialTheme.atcColors
    val description = pluralStringResource(R.plurals.cd_star_rating, stars, stars)
    Text(
        text = buildString {
            repeat(3) { index -> append(if (index < stars) "★" else "☆") }
        },
        modifier = modifier.semantics { contentDescription = description },
        color = colors.amber,
        fontSize = if (compact) 12.sp else 18.sp,
        letterSpacing = if (compact) 1.sp else 3.sp,
    )
}

@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
) {
    Canvas(modifier.size(10.dp)) {
        if (pulse) drawCircle(color.copy(alpha = .18f), radius = size.minDimension / 2f)
        drawCircle(color, radius = size.minDimension * .26f)
    }
}

@Composable
fun TinyPlane(modifier: Modifier = Modifier, color: Color? = null) {
    val resolvedColor = color ?: MaterialTheme.atcColors.green
    Canvas(modifier.size(22.dp)) {
        val path = Path().apply {
            moveTo(size.width * .50f, size.height * .06f)
            lineTo(size.width * .61f, size.height * .43f)
            lineTo(size.width * .94f, size.height * .62f)
            lineTo(size.width * .91f, size.height * .72f)
            lineTo(size.width * .58f, size.height * .64f)
            lineTo(size.width * .57f, size.height * .89f)
            lineTo(size.width * .70f, size.height * .97f)
            lineTo(size.width * .30f, size.height * .97f)
            lineTo(size.width * .43f, size.height * .89f)
            lineTo(size.width * .42f, size.height * .64f)
            lineTo(size.width * .09f, size.height * .72f)
            lineTo(size.width * .06f, size.height * .62f)
            lineTo(size.width * .39f, size.height * .43f)
            close()
        }
        drawPath(path, resolvedColor)
    }
}

@Composable
fun localizedInteger(value: Number): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    return formatter.format(value)
}
