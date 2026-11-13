package org.TheIza.fortrush.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.TheIza.fortrush.R
import org.TheIza.fortrush.ui.MainDestination

@Composable
fun FortRushBottomBar(
    selected: MainDestination,
    onSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .aspectRatio(4.25f),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.barra_navegacion),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillBounds
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 38.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(MainDestination.CARDS, selected, R.drawable.nav_cartas, "Cartas", onSelected, Modifier.weight(1f))
            NavItem(MainDestination.HOME, selected, R.drawable.nav_inicio, "Inicio", onSelected, Modifier.weight(1f))
            NavItem(MainDestination.PROFILE, selected, R.drawable.nav_perfil, "Perfil", onSelected, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(
    destination: MainDestination,
    selected: MainDestination,
    icon: Int,
    description: String,
    onSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(if (selected == destination) 76.dp else 66.dp)
                .background(
                    if (selected == destination) Color(0x66FFD23F) else Color.Transparent,
                    CircleShape
                )
                .clickable(role = Role.Tab) { onSelected(destination) },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = description,
                modifier = Modifier.size(if (selected == destination) 68.dp else 58.dp)
            )
        }
    }
}
