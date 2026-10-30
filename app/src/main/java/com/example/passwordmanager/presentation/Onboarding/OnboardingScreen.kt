package com.example.passwordmanager.presentation.Onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.passwordmanager.ui.theme.Spacing
import kotlinx.coroutines.launch

private class OnboardingPage(
    val icon: ImageVector,
    val badges: List<ImageVector>,
    val headline: String,
    val lines: List<String>
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Rounded.Key,
        badges = listOf(Icons.Rounded.Password, Icons.Rounded.Search),
        headline = "All your passwords in one place",
        lines = listOf("Find any login with search, star your favourites and create strong passwords with the built-in generator.")
    ),
    OnboardingPage(
        icon = Icons.Rounded.Shield,
        badges = listOf(Icons.Rounded.Timer, Icons.Rounded.QrCode),
        headline = "Your 2-step codes, built in",
        lines = listOf("Add TOTP codes from a QR code or a setup key, or import them from Google Authenticator.")
    ),
    OnboardingPage(
        icon = Icons.Rounded.StickyNote2,
        badges = listOf(Icons.Rounded.Lock),
        headline = "Private notes, encrypted too",
        lines = listOf("Keep Wi-Fi keys, PINs and recovery codes in notes that are encrypted like the rest of your vault.")
    ),
    OnboardingPage(
        icon = Icons.Rounded.PhoneAndroid,
        badges = listOf(Icons.Rounded.Lock, Icons.Rounded.Fingerprint),
        headline = "Locked down by design",
        lines = listOf(
            "Everything stays on this device. Vaultly has no internet access.",
            "Your whole vault is encrypted with SQLCipher (AES-256).",
            "Argon2id turns your master password into the key. The password itself is never stored and can't be recovered.",
            "Vaultly locks itself automatically, and screenshots are blocked.",
            "Fingerprint unlock is optional. Its key is kept in the Android Keystore.",
            "Backups are encrypted with a password you choose."
        )
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.l)
    ) {
        // Hidden, not removed, on the last page so the pager doesn't jump up.
        TextButton(
            onClick = onFinish,
            enabled = !isLastPage,
            modifier = Modifier
                .align(Alignment.End)
                .alpha(if (isLastPage) 0f else 1f)
        ) {
            Text(text = "Skip")
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            PageContent(page = pages[index], isPrimary = index % 2 == 0)
        }
        PageIndicator(
            pageCount = pages.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = Spacing.l)
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (isLastPage) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        ) {
            Text(text = if (isLastPage) "Get started" else "Next")
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage, isPrimary: Boolean) {
    // heightIn(min) gives the scrolling Column room to centre in, as in LockScreenLayout.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .padding(horizontal = Spacing.s, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Illustration(page = page, isPrimary = isPrimary)
            Text(
                text = page.headline,
                modifier = Modifier.padding(top = Spacing.l),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            page.lines.forEach { line ->
                Text(
                    text = line,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Illustration(page: OnboardingPage, isPrimary: Boolean) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isPrimary) colors.primaryContainer else colors.secondaryContainer
    val contentColor = if (isPrimary) colors.onPrimaryContainer else colors.onSecondaryContainer
    val badgeAlignments = listOf(Alignment.TopEnd, Alignment.BottomStart)
    Box(modifier = Modifier.size(168.dp)) {
        Box(
            modifier = Modifier
                .size(152.dp)
                .align(Alignment.Center)
                .background(containerColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = page.icon, contentDescription = null, modifier = Modifier.size(72.dp), tint = contentColor)
        }
        page.badges.zip(badgeAlignments).forEach { (badge, alignment) ->
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .align(alignment)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = badge, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.onPrimary)
            }
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (isSelected) 24.dp else 8.dp)
                    .background(color, CircleShape)
            )
        }
    }
}
