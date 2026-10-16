package ai.guardian.core.designsystem

import ai.guardian.engine.model.RiskLevel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun riskBadgeAlwaysShowsTextNotJustColour() {
        rule.setContent { GuardianTheme { RiskBadge(RiskLevel.HIGH) } }
        rule.onNodeWithText("High risk").assertIsDisplayed()
    }

    @Test
    fun riskGaugeIsReadableByScreenReaders() {
        rule.setContent { GuardianTheme { RiskGauge(score = 74, level = RiskLevel.HIGH) } }
        rule.onNodeWithContentDescription("Risk score 74 out of 100, High").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsTitleAndBody() {
        rule.setContent {
            GuardianTheme {
                EmptyState(icon = Icons.Default.Info, title = "Nothing checked yet", body = "Results will show up here.")
            }
        }
        rule.onNodeWithText("Nothing checked yet").assertIsDisplayed()
        rule.onNodeWithText("Results will show up here.").assertIsDisplayed()
    }
}
