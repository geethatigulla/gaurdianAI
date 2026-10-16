package ai.guardian

import ai.guardian.core.designsystem.GuardianTheme
import ai.guardian.feature.onboarding.OnboardingScreen
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun nextWalksThroughAllPagesThenFinishes() {
        var finished = 0
        rule.setContent { GuardianTheme { OnboardingScreen(onFinished = { finished++ }) } }

        rule.onNodeWithText("Spot scams in one tap").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Works in every app").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Private by design").assertIsDisplayed()
        rule.onNodeWithText("Get started").performClick()

        rule.runOnIdle { assertEquals(1, finished) }
    }

    @Test
    fun skipFinishesImmediately() {
        var finished = 0
        rule.setContent { GuardianTheme { OnboardingScreen(onFinished = { finished++ }) } }
        rule.onNodeWithText("Skip").performClick()
        rule.runOnIdle { assertEquals(1, finished) }
    }
}
