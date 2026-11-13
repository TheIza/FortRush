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

private val FortRushRoundedFont = FontFamily(
    Typeface.create("sans-serif-rounded", Typeface.NORMAL)
)

@Composable
fun FortRushScreenBackground(resourceId: Int) {
    Image(
        painter = painterResource(resourceId),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun CoinCounter(coins: Int, modifier: Modifier = Modifier) {
    val formatted = remember(coins) {
        NumberFormat.getIntegerInstance(Locale("es", "ES")).format(coins.coerceAtLeast(0))
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE8172115))
            .border(2.dp, Color(0xFFFFD46A), RoundedCornerShape(24.dp))
            .semantics { contentDescription = "Monedas: $formatted" }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.moneda),
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = formatted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFFFFF4CE),
            fontFamily = FortRushRoundedFont,
            fontWeight = FontWeight.Black,
            fontSize = 16.sp
        )
    }
}

@Composable
fun PlayerRibbon(userName: String, modifier: Modifier = Modifier) {
    val displayName = userName.ifBlank { "Jugador" }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFB9322B), Color(0xFF791D1B))))
            .border(2.dp, Color(0xFFE6A04B), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.avatar_rey),
            contentDescription = "Avatar de jugador",
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .border(2.dp, Color(0xFFFFD46A), CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "JUGADOR",
                color = Color(0xFFFFD46A),
                fontFamily = FortRushRoundedFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1
            )
            Text(
                text = displayName.uppercase(Locale.getDefault()),
                color = Color.White,
                fontFamily = FortRushRoundedFont,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
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
        Text(
            text = title,
            color = Color.White,
            fontFamily = FortRushRoundedFont,
            fontWeight = FontWeight.Black,
            fontSize = 25.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = Color(0xFFE1E8D2),
            fontFamily = FortRushRoundedFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FortRushAttackButton(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val enabled = onClick != null
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .semantics {
                contentDescription = if (enabled) "Atacar" else "Atacar, emparejamiento todavía no disponible"
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = { onClick?.invoke() }),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.fr_boton_atacar),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            alpha = if (enabled) 1f else 0.76f
        )
        Text(
            text = "¡ATACAR!",
            color = if (enabled) Color(0xFF542807) else Color(0xFF725B40),
            fontFamily = FortRushRoundedFont,
            fontWeight = FontWeight.Black,
            fontSize = 25.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FortRushCardTile(card: CardCatalogItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.735f),
            contentAlignment = Alignment.TopCenter
        ) {
            Image(
                painter = painterResource(R.drawable.fr_marco_carta),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            Image(
                painter = painterResource(card.imageResource),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .fillMaxHeight(0.72f)
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                contentScale = ContentScale.Fit
            )
        }
        Text(
            text = card.name,
            color = Color.White,
            fontFamily = FortRushRoundedFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 24.dp)
                .semantics { contentDescription = card.coinCost.toString() + " monedas" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.moneda),
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = card.coinCost.toString(),
                color = Color(0xFFFFE27A),
                fontFamily = FortRushRoundedFont,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                maxLines = 1
            )
        }
    }
}