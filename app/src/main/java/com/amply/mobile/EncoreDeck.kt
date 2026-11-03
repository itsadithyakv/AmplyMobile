package com.amply.mobile

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amply.mobile.domain.Song
import com.amply.mobile.stats.Encore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** Each card's two gradient stops and the ink that reads on them (desktop Encore.tsx PALETTES). */
private data class CardPalette(val from: Color, val to: Color, val ink: Color)

private val Intro = CardPalette(Color(0xFF5B21B6), Color(0xFFDB2777), Color.White)
private val Minutes = CardPalette(Color(0xFFEA580C), Color(0xFFDC2626), Color.White)
private val Genre = CardPalette(Color(0xFF0E7490), Color(0xFF1D4ED8), Color.White)
private val Artist = CardPalette(Color(0xFFFACC15), Color(0xFFF97316), Color(0xFF1C1917))
private val SongCard = CardPalette(Color(0xFFBE185D), Color(0xFF6D28D9), Color.White)
private val Top = CardPalette(Color(0xFF15803D), Color(0xFF0F766E), Color.White)
private val PersonaCard = CardPalette(Color(0xFF1E1B4B), Color(0xFF7C3AED), Color.White)
private val Streak = CardPalette(Color(0xFFA3E635), Color(0xFF16A34A), Color(0xFF14200F))
private val MonthCard = CardPalette(Color(0xFFF472B6), Color(0xFFFB923C), Color(0xFF1F1020))
private val Share = CardPalette(Color(0xFF111827), Color(0xFF4C1D95), Color.White)

private const val CARD_MS = 7_000L

@Composable
fun EncoreDeck(encore: Encore, songsById: Map<Long, Song>, onPlayMix: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf<String?>(null) }
    val monthName = Month.of(encore.busiestMonth).getDisplayName(TextStyle.FULL, Locale.getDefault())
    val hourLabel = "%d:00".format(encore.peakHour)
    val cards: List<@Composable () -> Unit> = buildList {
        add { CardBody(Intro, "Your ${encore.year}, encore!", big = "🎶", line = "The songs, the artists, the late nights. Tap to play it back.") }
        add {
            val days = encore.minutes / (60 * 24)
            val hours = encore.minutes / 60
            CardBody(
                Minutes,
                "You spent",
                big = "%,d".format(encore.minutes),
                line = "minutes with music",
                foot = when {
                    days >= 1 -> if (days == 1L) "That's a whole day, nonstop." else "That's $days whole days, nonstop."
                    hours >= 1 -> if (hours == 1L) "That's a whole hour, nonstop." else "That's $hours hours, nonstop."
                    else -> null
                },
            )
        }
        encore.topGenres.firstOrNull()?.let { genre -> add { CardBody(Genre, "Your year sounded like", big = genre.label, line = "It was on repeat more than anything else.") } }
        encore.topArtists.firstOrNull()?.let { artist ->
            add { CardBody(Artist, "Your number one artist", big = artist.label, line = "${artist.plays} plays. Safe to say you're a fan.", song = artist.songId?.let(songsById::get)) }
        }
        encore.topSongs.firstOrNull()?.let { song ->
            add {
                CardBody(
                    SongCard,
                    "Your song of the year",
                    big = song.label,
                    line = song.sublabel,
                    foot = if (song.plays == 1) "played once" else "played ${song.plays} times",
                    song = song.songId?.let(songsById::get),
                )
            }
        }
        if (encore.topSongs.size > 1) {
            add {
                CardFrame(Top) {
                    Lead("Your top five", Top.ink)
                    Spacer(Modifier.height(18.dp))
                    encore.topSongs.forEachIndexed { index, entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", color = Top.ink, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(38.dp))
                            entry.songId?.let(songsById::get)?.let {
                                ArtworkBox(it.artworkUri, 48.dp, shape = RadiusSm, audioUri = it.contentUri)
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(entry.label, color = Top.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                entry.sublabel?.let { Text(it, color = Top.ink.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
        }
        add { CardBody(PersonaCard, "Your listening personality", big = encore.persona.emoji, title = encore.persona.title, line = encore.persona.body, foot = "Your peak hour: $hourLabel") }
        add {
            CardBody(
                Streak,
                "Your longest streak",
                big = encore.longestStreak.toString(),
                line = if (encore.longestStreak == 1) "day in a row with music. A start!" else "days in a row with music. Never skipped a beat.",
            )
        }
        add { CardBody(MonthCard, "Your loudest month", big = monthName, line = "Nothing else came close.") }
        add {
            CardFrame(Share) {
                Lead("What a year.", Share.ink)
                Spacer(Modifier.height(12.dp))
                Text("Play the songs that made it, or save your Encore card to share.", color = Share.ink.copy(alpha = 0.85f), fontSize = 17.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                DeckButton("Play my Encore mix", filled = true, onClick = onPlayMix)
                Spacer(Modifier.height(12.dp))
                DeckButton("Save card", filled = false) {
                    scope.launch {
                        saved = withContext(Dispatchers.IO) { saveEncoreCard(context, encore) }
                    }
                }
                saved?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Share.ink.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val pager = rememberPagerState { cards.size }
        // Auto-advance like stories; any page change restarts the clock. The last card stays.
        LaunchedEffect(pager.currentPage) {
            if (pager.currentPage < cards.lastIndex) {
                delay(CARD_MS)
                pager.animateScrollToPage(pager.currentPage + 1)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTapGestures { offset ->
                                val target = if (offset.x > size.width / 3f) page + 1 else page - 1
                                if (target in cards.indices) scope.launch { pager.animateScrollToPage(target) }
                            }
                        },
                ) { cards[page]() }
            }
            // Story-style progress segments and a close button.
            Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    cards.indices.forEach { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (index <= pager.currentPage) 0.95f else 0.35f)),
                        )
                    }
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close Encore", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun CardFrame(palette: CardPalette, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(palette.from, palette.to))).safeDrawingPadding().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun Lead(text: String, ink: Color) {
    Text(text.uppercase(), color = ink.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.14.em, textAlign = TextAlign.Center)
}

@Composable
private fun CardBody(
    palette: CardPalette,
    lead: String,
    big: String,
    line: String? = null,
    title: String? = null,
    foot: String? = null,
    song: Song? = null,
) {
    CardFrame(palette) {
        Lead(lead, palette.ink)
        Spacer(Modifier.height(22.dp))
        if (song != null) {
            ArtworkBox(song.artworkUri, 180.dp, shape = RadiusLg, audioUri = song.contentUri)
            Spacer(Modifier.height(22.dp))
        }
        Text(
            big,
            color = palette.ink,
            fontSize = if (big.length > 14) 38.sp else 64.sp,
            lineHeight = if (big.length > 14) 42.sp else 66.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.04).em,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        title?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = palette.ink, fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
        line?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = palette.ink.copy(alpha = 0.9f), fontSize = 18.sp, textAlign = TextAlign.Center)
        }
        foot?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = palette.ink.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DeckButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (filled) Share.from else Color.White,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.16f))
            .clickableNoRipple(onClick)
            .padding(vertical = 15.dp),
    )
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    pointerInput(onClick) { detectTapGestures { onClick() } }

/**
 * Draws a 1080x1920 share card (gradient, year, minutes, top artist and song, persona) and saves it
 * to Pictures/Amply. Returns a message for the user.
 */
fun saveEncoreCard(context: Context, encore: Encore): String {
    val width = 1080
    val height = 1920
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawPaint(Paint().apply {
        shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), Share.from.toArgb(), Intro.to.toArgb(), Shader.TileMode.CLAMP)
    })
    val font = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.zt_nature_black) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    val body = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.zt_nature_medium) }.getOrNull() ?: Typeface.DEFAULT
    fun text(value: String, y: Float, size: Float, face: Typeface, alpha: Int = 255) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            this.alpha = alpha
            textSize = size
            typeface = face
            textAlign = Paint.Align.CENTER
        }
        var shown = value
        while (paint.measureText(shown) > width - 140 && shown.length > 4) shown = shown.dropLast(2) + "…"
        canvas.drawText(shown, width / 2f, y, paint)
    }
    text("AMPLY ENCORE ${encore.year}", 260f, 44f, body, 210)
    text("%,d".format(encore.minutes), 560f, 200f, font)
    text("minutes with music", 650f, 52f, body, 230)
    encore.topArtists.firstOrNull()?.let {
        text("NUMBER ONE ARTIST", 900f, 36f, body, 200)
        text(it.label, 990f, 84f, font)
    }
    encore.topSongs.firstOrNull()?.let {
        text("SONG OF THE YEAR", 1180f, 36f, body, 200)
        text(it.label, 1270f, 76f, font)
    }
    text("${encore.persona.emoji}  ${encore.persona.title}", 1500f, 64f, font)
    text("${encore.plays} plays  ·  ${encore.longestStreak}-day streak", 1600f, 44f, body, 220)
    text("Amply", 1800f, 56f, font, 230)

    val name = "Amply-Encore-${encore.year}.png"
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Amply")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no media store")
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "Saved to Pictures/Amply/$name"
    } catch (error: Exception) {
        "Couldn't save the card: ${error.message}"
    } finally {
        bitmap.recycle()
    }
}
