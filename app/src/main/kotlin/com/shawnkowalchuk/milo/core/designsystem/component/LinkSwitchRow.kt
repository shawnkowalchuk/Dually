package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** Android's smallest target for a finger. */
private val FingerTarget = 48.dp

/** How high a title with the line under it is drawn, as in `TitledSwitchRow`. */
private val DrawnHeight = 36.dp

/** The arrowhead after the words, as large as the one of `TileLinkLabel`. */
private val ChevronSize = 18.dp

/**
 * A row of a tile that is **two things to press**: its words, which lead to another screen, and
 * a switch at its end, which is pressed by itself. For a setting that is both on or off and one
 * of many, where the many have a screen of their own: "Connect", with the sound in use under
 * it, an arrowhead, and the switch that silences it (Settings' "Sounds" tile, 2026-10-13, its
 * first user).
 *
 * It is `TitledSwitchRow`'s title and line with the arrowhead of `TileLinkLabel` after them,
 * beside the switch every other part draws (`SwitchMark`). Where a row is pressed as a whole,
 * use `TitledSwitchRow` (the press toggles) or a tile with a `TilePress` (the press leads on);
 * this one is only for a row that has to do both.
 *
 * **The switch does not lead on, and the words do not toggle.** From the start of the row to
 * the arrowhead is one button; the switch is a second target, 48 dp wide and high, 12 dp after
 * the arrowhead.
 *
 * It is drawn as high as its two lines and takes up no more. Each of the two places a finger can
 * hit is 48 dp high: it reaches 6 dp past what is drawn, above and below, so the rows of one
 * tile stand at least 12 dp apart, and nothing else that can be pressed may stand closer. A row
 * whose words need more than the 48 dp is left as high as it is.
 *
 * **A screen reader finds two things.** The words, read as one button: the title, the line,
 * and after "double tap to" what [link] says the press does. And the switch, read by
 * [switchName] with its state, because the words beside it belong to the other button.
 *
 * @param line the quieter line: which of the many is in force.
 * @param link where the words lead, with what a screen reader says the press does.
 * @param switchName what a screen reader says the switch is: "Connect sound".
 */
@Composable
fun LinkSwitchRow(
    title: String,
    line: String,
    link: TilePress,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchName: String,
    modifier: Modifier = Modifier,
) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.fillMaxWidth().takesUpOnly(DrawnHeight),
        horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = FingerTarget)
                    .clickable(
                        onClickLabel = link.label,
                        role = Role.Button,
                        onClick = link.onClick,
                    ),
            horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap),
            ) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = line, style = MiloTheme.textStyles.tileLabel, color = grey)
            }
            Icon(
                imageVector = ChevronIcons.Forward,
                // The button's own press label says where it leads.
                contentDescription = null,
                modifier = Modifier.size(ChevronSize),
                tint = grey,
            )
        }
        Box(
            modifier =
                Modifier
                    .sizeIn(minWidth = FingerTarget, minHeight = FingerTarget)
                    .toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    ).semantics { contentDescription = switchName },
            contentAlignment = Alignment.Center,
        ) {
            SwitchMark(checked = checked)
        }
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped.
@Preview
@Composable
private fun LinkSwitchRowPreview() {
    MiloTheme {
        Surface {
            Tile(padding = TilePadding.EVEN, gap = MiloTheme.spacing.rowGap) {
                TileLabel(text = "Sounds")
                LinkSwitchRow(
                    title = "Connect",
                    line = "Seatbelt chime",
                    link = TilePress("choose the connect sound") {},
                    checked = true,
                    onCheckedChange = {},
                    switchName = "Connect sound",
                )
                LinkSwitchRow(
                    title = "Trip start",
                    line = "mk64_racestart.mp3",
                    link = TilePress("choose the trip-start sound") {},
                    checked = false,
                    onCheckedChange = {},
                    switchName = "Trip-start sound",
                )
            }
        }
    }
}
