package com.cleanlr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleanlr.model.HslAdjustments
import com.cleanlr.model.HslChannel
import com.cleanlr.ui.theme.SpectrumBorder
import com.cleanlr.ui.theme.SpectrumTextPrimary

enum class HslColor(val title: String, val color: Color) {
    RED("Red", Color(0xFFE53935)),
    ORANGE("Orange", Color(0xFFFB8C00)),
    YELLOW("Yellow", Color(0xFFFDD835)),
    GREEN("Green", Color(0xFF43A047)),
    AQUA("Aqua", Color(0xFF00ACC1)),
    BLUE("Blue", Color(0xFF1E88E5)),
    PURPLE("Purple", Color(0xFF8E24AA)),
    MAGENTA("Magenta", Color(0xFFD81B60))
}

@Composable
fun HslMixerPanel(
    hsl: HslAdjustments,
    onHslChanged: (HslAdjustments) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedColor by remember { mutableStateOf(HslColor.RED) }

    fun getChannel(color: HslColor): HslChannel = when (color) {
        HslColor.RED -> hsl.red
        HslColor.ORANGE -> hsl.orange
        HslColor.YELLOW -> hsl.yellow
        HslColor.GREEN -> hsl.green
        HslColor.AQUA -> hsl.aqua
        HslColor.BLUE -> hsl.blue
        HslColor.PURPLE -> hsl.purple
        HslColor.MAGENTA -> hsl.magenta
    }

    fun updateChannel(color: HslColor, channel: HslChannel) {
        val updated = when (color) {
            HslColor.RED -> hsl.copy(red = channel)
            HslColor.ORANGE -> hsl.copy(orange = channel)
            HslColor.YELLOW -> hsl.copy(yellow = channel)
            HslColor.GREEN -> hsl.copy(green = channel)
            HslColor.AQUA -> hsl.copy(aqua = channel)
            HslColor.BLUE -> hsl.copy(blue = channel)
            HslColor.PURPLE -> hsl.copy(purple = channel)
            HslColor.MAGENTA -> hsl.copy(magenta = channel)
        }
        onHslChanged(updated)
    }

    val activeChannel = getChannel(selectedColor)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Swatches Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HslColor.values().forEach { col ->
                val isSelected = col == selectedColor
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(col.color)
                        .clickable { selectedColor = col }
                        .then(
                            if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier.border(1.dp, SpectrumBorder, CircleShape)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "${selectedColor.title} Color Mixer",
            color = SpectrumTextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        RampedSlider(
            label = "Hue",
            value = activeChannel.hue,
            minVal = -100f,
            maxVal = 100f,
            onValueChange = { updateChannel(selectedColor, activeChannel.copy(hue = it)) }
        )
        RampedSlider(
            label = "Saturation",
            value = activeChannel.saturation,
            minVal = -100f,
            maxVal = 100f,
            onValueChange = { updateChannel(selectedColor, activeChannel.copy(saturation = it)) }
        )
        RampedSlider(
            label = "Luminance",
            value = activeChannel.luminance,
            minVal = -100f,
            maxVal = 100f,
            onValueChange = { updateChannel(selectedColor, activeChannel.copy(luminance = it)) }
        )
    }
}
