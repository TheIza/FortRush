package org.TheIza.fortrush.ui.components

import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.model.CardCatalogItem
import java.text.NumberFormat
import java.util.Locale

private val FortRushRoundedFont = FontFamily(Typeface.create("sans-serif-rounded", Typeface.NORMAL))
private val Gold = Color(0xFFFFD46A)

@Composable
fun FortRushScreenBackground(resourceId: Int) {
    Image(painter = painterResource(resourceId), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}

@Composable
fun CoinCounter(coins: Int, modifier: Modifier = Modifier) {
    val formatted = remember(coins) { NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES")).format(coins.coerceAtLeast(0)) }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE8172115))
            .border(1.5.dp, Gold, RoundedCornerShape(24.dp))
            .semantics { contentDescription = "Monedas: $formatted" }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Image(painter = painterResource(R.drawable.moneda), contentDescription = null, modifier = Modifier.size(27.dp))
        Text(formatted, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, color = Color(0xFFFFF4CE), fontFamily = FortRushRoundedFont, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

@Composable
fun PlayerRibbon(userName: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xE52D442B), Color(0xE5192B20))))
            .border(1.5.dp, Color(0xFFB8C982), RoundedCornerShape(18.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.avatar_rey),
            contentDescription = "Avatar de jugador",
            modifier = Modifier.size(48.dp).clip(CircleShape).border(1.5.dp, Gold, CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.size(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("TU CASTILLO", color = Gold, fontFamily = FortRushRoundedFont, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp, maxLines = 1)
            Text(
                text = userName.ifBlank { "Jugador" },
                color = Color.White,
                fontFamily = FortRushRoundedFont,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FortRushPageTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = Color.White, fontFamily = FortRushRoundedFont, fontWeight = FontWeight.Black, fontSize = 25.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = Color(0xFFE1E8D2), fontFamily = FortRushRoundedFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun FortRushAttackButton(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val enabled = onClick != null
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .semantics { contentDescription = if (enabled) "Jugar" else "Jugar todavía no disponible; emparejamiento en preparación" }
            .clickable(enabled = enabled, role = Role.Button, onClick = { onClick?.invoke() }),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(R.drawable.fr_boton_atacar), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, alpha = if (enabled) 1f else 0.8f)
        Text(
            text = if (enabled) "JUGAR" else "PRÓXIMAMENTE",
            color = if (enabled) Color(0xFF542807) else Color(0xFF725B40),
            fontFamily = FortRushRoundedFont,
            fontWeight = FontWeight.Black,
            fontSize = if (enabled) 23.sp else 16.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FortRushCardTile(card: CardCatalogItem, modifier: Modifier = Modifier) {
    val fontScale = LocalDensity.current.fontScale
    val cardAspectRatio = if (fontScale >= 1.2f) 0.56f else 0.62f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(cardAspectRatio)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF0213A48), Color(0xF411211B))))
            .border(1.5.dp, Color(0xFFD5AD57), RoundedCornerShape(20.dp))
            .semantics { contentDescription = "${card.name}, ${card.category.lowercase()}, ${card.summary}, ${card.coinCost} monedas" }
            .padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            card.category,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF314D55)).padding(horizontal = 9.dp, vertical = 3.dp),
            color = Color(0xFFFFE29A), fontFamily = FortRushRoundedFont, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp
        )
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(13.dp)).background(Color(0xFF244C6B)),
            contentAlignment = Alignment.Center
        ) {
            Image(painter = painterResource(card.imageResource), contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        }
        Text(card.name, color = Color.White, fontFamily = FortRushRoundedFont, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 2, softWrap = true, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
        Text(card.summary, color = Color(0xFFD1DDCF), fontFamily = FortRushRoundedFont, fontWeight = FontWeight.Medium, fontSize = 10.sp, maxLines = 2, softWrap = true, textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
        Row(
            modifier = Modifier.defaultMinSize(minHeight = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(painter = painterResource(R.drawable.moneda), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(card.coinCost.toString(), color = Color(0xFFFFE27A), fontFamily = FortRushRoundedFont, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1)
        }
    }
}