package com.maknoon.mizan.ui.help

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.ui.component.AppTopBar
import com.maknoon.mizan.ui.component.AppTopBarRow

/**
 * The in-app guide from docs/04: what every screen and figure means, written for someone who
 * has never seen the app instead of for its author.
 *
 * It is opened from the About screen and keeps the same dark chrome and card rhythm, so a
 * confused user reaches an answer in three taps from any tab without leaving the app.
 */
@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar {
            AppTopBarRow {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.help_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.help_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.help_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            guideItem("quick", R.string.help_quick_title, R.string.help_quick_body)
            guideItem("dashboard", R.string.help_dashboard_title, R.string.help_dashboard_body)
            guideItem("notification", R.string.help_notification_title, R.string.help_notification_body)
            guideItem("history", R.string.help_history_title, R.string.help_history_body)
            guideItem("apps", R.string.help_apps_title, R.string.help_apps_body)
            guideItem("budget", R.string.help_budget_title, R.string.help_budget_body)
            guideItem("saver", R.string.help_saver_title, R.string.help_saver_body)

            item(key = "faqHeader") {
                Text(
                    text = stringResource(R.string.help_faq_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
            }
            guideItem(
                "faqInternet",
                R.string.help_faq_no_internet_title,
                R.string.help_faq_no_internet_body,
            )
            guideItem("faqLess", R.string.help_faq_less_title, R.string.help_faq_less_body)
            guideItem("faqDash", R.string.help_faq_dash_title, R.string.help_faq_dash_body)
            guideItem("faqAccuracy", R.string.help_faq_accuracy_title, R.string.help_faq_accuracy_body)
        }
    }
}

/** One titled card; the key keeps LazyColumn items stable while scrolling a long guide. */
private fun LazyListScope.guideItem(
    key: String,
    @StringRes title: Int,
    @StringRes body: Int,
) {
    item(key = key) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "Help — Arabic", locale = "ar", showBackground = true, heightDp = 720)
@Composable
private fun HelpScreenPreview() {
    MizanTheme(darkTheme = true, dynamicColor = false) {
        HelpScreen()
    }
}
