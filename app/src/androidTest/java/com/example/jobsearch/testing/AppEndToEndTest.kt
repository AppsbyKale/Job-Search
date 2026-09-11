package com.example.jobsearch.testing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.jobsearch.MainActivity
import org.junit.Rule
import org.junit.Test

/**
 * Comprehensive End-to-End (E2E) Test suite for JobSearch app launching MainActivity,
 * verifying main screens, Settings dropdown menu, navigation, and resume section.
 */
class AppEndToEndTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun givenAppLaunch_whenStarted_displaysJobSearchMainTitle() {
        // 1. Verify Main Job Search screen title is displayed on launch
        composeTestRule.onNodeWithText("Job Search").assertIsDisplayed()
    }

    @Test
    fun givenMainScreen_whenSettingsIconClicked_displaysSettingsDropdownMenu() {
        // 1. Verify app launches on Job Search screen
        composeTestRule.onNodeWithText("Job Search").assertIsDisplayed()

        // 2. Tap Settings gear icon in TopAppBar
        composeTestRule.onNodeWithContentDescription("Settings").performClick()

        // 3. Assert that the Settings dropdown menu opens displaying Resume at the top
        composeTestRule.onNodeWithTag("menu_item_resume").assertIsDisplayed()
    }

    @Test
    fun givenSettingsDropdown_whenResumeSelected_navigatesToResumeSection() {
        // 1. Tap Settings icon on main screen
        composeTestRule.onNodeWithContentDescription("Settings").performClick()

        // 2. Tap "Resume" item in dropdown menu using its unique test tag
        composeTestRule.onNodeWithTag("menu_item_resume").performClick()

        // 3. Assert that Settings screen opens showing "Your resume" section header
        composeTestRule.onNodeWithText("Your resume").assertIsDisplayed()
    }
}
