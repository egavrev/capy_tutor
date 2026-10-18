package com.capytutor.ui.corner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.capytutor.AppContainer
import com.capytutor.R
import com.capytutor.core.RomanianText
import com.capytutor.core.reward.Corner
import com.capytutor.core.reward.CornerItem
import com.capytutor.core.reward.Tap
import com.capytutor.data.repo.CornerState
import com.capytutor.ui.components.BowCounter
import com.capytutor.ui.components.CapyBottomNav
import com.capytutor.ui.components.ChildTab
import com.capytutor.ui.theme.CapyDimens
import com.capytutor.ui.theme.CapyShapes
import com.capytutor.ui.theme.CapyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CornerViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<CornerState?> = c.corner.observe().map<CornerState, CornerState?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Îți mai trebuie 5 fundițe" after a tap on something she can't afford yet. */
    val message = MutableStateFlow<String?>(null)

    fun tap(item: CornerItem) = viewModelScope.launch {
        val t = withContext(Dispatchers.IO) { c.corner.tap(item) }
        message.value = (t as? Tap.NotEnough)?.let { "Îți mai trebuie " + RomanianText.count(it.missing, "fundiță", "fundițe") }
    }
}

/**
 * Colțul lui Capy (E12, design "Capy Tutore Pink"): Capy's room and a little shop. She spends the
 * bows she earned on things for the room; a thing she owns can go out of the room and back for
 * free. The room is the design's own drawing, rendered to stacked images (light and dark, see
 * tools/corner-render.py); Capy sits between the pillow's back and front.
 */
@Composable
fun CornerScreen(vm: CornerViewModel, onTab: (ChildTab) -> Unit, onParent: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val corner = state?.corner ?: Corner()
    val balance = state?.balance ?: 0
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CapyDimens.screenMargin, vertical = CapyDimens.itemGap * 2),
            verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap * 2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
                CloudTitle(Modifier.weight(1f))
                BowCounter(balance)
            }
            Room(corner)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
                Text(
                    "Lucruri pentru colț",
                    style = CapyTheme.type.label.copy(fontWeight = FontWeight.Bold),
                    color = CapyTheme.colors.text,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    message.orEmpty(),
                    style = CapyTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = CapyTheme.colors.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            ShopGrid(corner, balance, vm::tap)
            ParentLink(onParent, Modifier.align(Alignment.CenterHorizontally))
        }
        CapyBottomNav(ChildTab.CORNER, onTab)
    }
}

/** The title on a soft cloud, with a small heart on each side. */
@Composable
private fun CloudTitle(modifier: Modifier) {
    val c = CapyTheme.colors
    Box(modifier.height(CapyDimens.cornerTitleHeight), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // The design's 232 x 56 cloud, stretched to the space it gets.
            val sx = size.width / 232f
            val sy = size.height / 56f
            drawRoundRect(c.soft, Offset(10 * sx, 14 * sy), Size(212 * sx, 30 * sy), CornerRadius(15 * sx, 15 * sy))
            for ((x, y, r) in CLOUD) drawOval(c.soft, Offset((x - r) * sx, (y - r) * sy), Size(2 * r * sx, 2 * r * sy))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap - CapyDimens.border * 2)) {
            Heart()
            Text(
                "Colțul lui Capy",
                style = CapyTheme.type.title.copy(fontWeight = FontWeight.Bold),
                color = c.text,
                maxLines = 1,
                modifier = Modifier.semantics { heading() },
            )
            Heart()
        }
    }
}

private val CLOUD = listOf(
    Triple(26f, 26f, 16f), Triple(54f, 18f, 15f), Triple(86f, 15f, 14f), Triple(118f, 15f, 14f), Triple(150f, 15f, 14f),
    Triple(180f, 18f, 15f), Triple(206f, 26f, 16f), Triple(40f, 40f, 13f), Triple(78f, 42f, 12f), Triple(116f, 42f, 12f),
    Triple(154f, 42f, 12f), Triple(192f, 40f, 13f),
)

@Composable
private fun Heart() {
    Icon(painterResource(R.drawable.ic_capy_heart_small), contentDescription = null, tint = CapyTheme.colors.primary, modifier = Modifier.size(CapyDimens.iconTiny))
}

/** The room in the design: 328 x 320, everything placed by the design's own coordinates. */
private const val ROOM_W = 328f
private const val ROOM_H = 320f

/** Layers back to front; the pillow is split so Capy sits inside it. */
private fun layers(corner: Corner): Pair<List<Int>, List<Int>> {
    fun on(item: CornerItem, res: Int) = if (corner.placed(item)) listOf(res) else emptyList()
    val behind = listOf(R.drawable.corner_bg) +
        on(CornerItem.WINDOW, R.drawable.corner_window) +
        on(CornerItem.SHELF, R.drawable.corner_shelf) +
        on(CornerItem.STICKERS, R.drawable.corner_stickers) +
        on(CornerItem.POSTERS, R.drawable.corner_posters) +
        on(CornerItem.PLANT, R.drawable.corner_plant) +
        on(CornerItem.LAMP, R.drawable.corner_lamp) +
        on(CornerItem.CUP, R.drawable.corner_cup) +
        on(CornerItem.RUG, R.drawable.corner_rug) +
        on(CornerItem.PILLOW, R.drawable.corner_pillow_back)
    val front = on(CornerItem.PILLOW, R.drawable.corner_pillow_front) +
        on(CornerItem.FOOD, R.drawable.corner_food) +
        on(CornerItem.PLUSH, if (corner.placed(CornerItem.PILLOW)) R.drawable.corner_plush_high else R.drawable.corner_plush_low)
    return behind to front
}

@Composable
private fun Room(corner: Corner) {
    val (behind, front) = layers(corner)
    val label = corner.inRoom.let { if (it.isEmpty()) "Camera lui Capy, încă goală" else "Camera lui Capy: " + it.joinToString(", ") { i -> i.say } }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ROOM_W / ROOM_H)
            .clip(CapyShapes.card)
            .semantics { contentDescription = label },
    ) {
        val unit = maxWidth / ROOM_W
        behind.forEach { Layer(it) }
        Image(
            painterResource(R.drawable.capy_neutral),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = unit * if (corner.placed(CornerItem.PILLOW)) 70f else 34f)
                .size(unit * 120f),
        )
        front.forEach { Layer(it) }
    }
}

@Composable
private fun Layer(res: Int) {
    Image(painterResource(res), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
}

private fun CornerItem.icon(): Int = when (this) {
    CornerItem.PILLOW -> R.drawable.ic_capy_cushion
    CornerItem.PLUSH -> R.drawable.ic_capy_plush
    CornerItem.SHELF -> R.drawable.ic_capy_shelf
    CornerItem.LAMP -> R.drawable.ic_capy_lamp
    CornerItem.PLANT -> R.drawable.ic_capy_plant
    CornerItem.RUG -> R.drawable.ic_capy_rug
    CornerItem.FOOD -> R.drawable.ic_capy_food
    CornerItem.CUP -> R.drawable.ic_capy_cup
    CornerItem.STICKERS -> R.drawable.ic_capy_sticker
    CornerItem.POSTERS -> R.drawable.ic_capy_poster
    CornerItem.WINDOW -> R.drawable.ic_capy_window
}

/** Four tiles a row: owned = `surface` (in the room: 2 dp `primary` border + check); not yet = Linen with the price. */
@Composable
private fun ShopGrid(corner: Corner, balance: Int, onTap: (CornerItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
        CornerItem.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap)) {
                row.forEach { item -> ItemTile(item, corner, balance, { onTap(item) }, Modifier.weight(1f)) }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ItemTile(item: CornerItem, corner: Corner, balance: Int, onTap: () -> Unit, modifier: Modifier) {
    val c = CapyTheme.colors
    val has = item in corner.owned
    val placed = corner.placed(item)
    val can = !has && balance >= item.price
    val price = RomanianText.count(item.price, "fundiță", "fundițe")
    val strong: Color = if (has || can) c.text else c.textSecondary
    Box(modifier) {
        Surface(
            color = if (has) c.surface else c.inactive,
            contentColor = c.text,
            shape = CapyShapes.card,
            border = if (placed) BorderStroke(CapyDimens.strokeIcon, c.primary) else BorderStroke(CapyDimens.border, c.line),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CapyDimens.cornerTileMinHeight)
                .clip(CapyShapes.card)
                .clickable(role = Role.Button, onClick = onTap)
                .semantics(mergeDescendants = true) {
                    contentDescription = when {
                        has -> item.label
                        can -> "${item.label}, costă $price, atinge ca să-l cumperi"
                        else -> "${item.label}, costă $price"
                    }
                    if (has) stateDescription = if (placed) "e în cameră, atinge ca să-l scoți" else "nu e în cameră, atinge ca să-l pui"
                },
        ) {
            Column(
                Modifier.padding(vertical = CapyDimens.itemGap - CapyDimens.border * 2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CapyDimens.border * 3, Alignment.CenterVertically),
            ) {
                Icon(painterResource(item.icon()), contentDescription = null, tint = if (has || can) c.text else c.controlOutline, modifier = Modifier.size(CapyDimens.icon))
                Text(item.label, style = CapyTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = strong, maxLines = 1)
                if (!has) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CapyDimens.border * 2)) {
                        val pc = if (can) c.primary else c.textSecondary
                        Icon(painterResource(R.drawable.ic_capy_bow), contentDescription = null, tint = pc, modifier = Modifier.size(CapyDimens.iconMini))
                        Text("${item.price}", style = CapyTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = pc)
                    }
                }
            }
        }
        if (placed) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(CapyDimens.border * 5)
                    .size(CapyDimens.placedBadge)
                    .background(c.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_capy_check), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(CapyDimens.iconMini))
            }
        }
    }
}

/** "Pentru părinți": the way into parent mode from this tab (behind the PIN, as on Acasă). */
@Composable
private fun ParentLink(onClick: () -> Unit, modifier: Modifier) {
    val c = CapyTheme.colors
    Row(
        modifier
            .heightIn(min = CapyDimens.touchTarget)
            .clip(CapyShapes.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = CapyDimens.itemGap * 1.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CapyDimens.itemGap - CapyDimens.border * 2),
    ) {
        Icon(painterResource(R.drawable.ic_capy_lock), contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(CapyDimens.iconSmall))
        Text("Pentru părinți", style = CapyTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.textSecondary)
    }
}
