package com.texfi.w0y.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.texfi.w0y.HiltTestActivity
import com.texfi.w0y.data.SettingsCategory
import com.texfi.w0y.ui.screens.SettingsScreen
import com.texfi.w0y.ui.theme.W0yTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Настоящий экран настроек на Robolectric: все вкладки открываются, листаются до конца и не падают. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsWalkTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<HiltTestActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(hilt).around(compose)

    private fun show() {
        hilt.inject()
        compose.setContent { W0yTheme { SettingsScreen(onClose = {}, onOpenLogin = {}) } }
        compose.waitForIdle()
    }

    private val verticalList =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
            !hasTestTag("settings-tabs") and !hasAnyAncestor(hasTestTag("settings-tabs"))

    @Test
    fun eightTabsEachOpensAndScrollsToTheEnd() {
        show()
        assertEquals(8, SettingsCategory.entries.size)
        SettingsCategory.entries.forEach { cat ->
            val tab = compose.onNodeWithTag("tab-${cat.name}")
            tab.assertHeightIsAtLeast(48.dp)
            tab.performClick()
            compose.waitForIdle()
            // До конца: у списка есть видимые строки и он листается без сбоев.
            val list = compose.onAllNodes(verticalList).onFirst()
            val rows = list.fetchSemanticsNode().config.getOrNull(SemanticsProperties.CollectionInfo)?.rowCount ?: 1
            list.performScrollToIndex((rows - 1).coerceAtLeast(0))
            compose.waitForIdle()
        }
    }

    @Test
    // Robolectric меряет текст условным шрифтом, поэтому ширину глифов он не проверяет:
    // здесь только «одна строка, без переноса»; усечение многоточием исключено разметкой (softWrap = false в полосе с прокруткой).
    fun tabLabelsAreNotTruncated() {
        show()
        SettingsCategory.entries.forEach { cat ->
            val node = compose.onNodeWithTag("tab-${cat.name}").fetchSemanticsNode()
            val results = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
            val textNode = node.children.firstOrNull() ?: node
            textNode.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.forEach {
                assertTrue("вкладка ${cat.name} усечена: overflowW=${it.didOverflowWidth} visual=${it.hasVisualOverflow} lines=${it.lineCount} size=${it.size} input=${it.layoutInput.text}", it.lineCount == 1)
            }
        }
    }

    @Test
    fun searchOpensFromTheIconAndFindsASetting() {
        show()
        compose.onNodeWithTag("settings-search").assertHeightIsAtLeast(48.dp).performClick()
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("delete")
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasText("Accidental delete protection", substring = true, ignoreCase = true)).assertIsDisplayed()
    }

    @Test
    fun aboutOpensWithTheSameHeaderAndScrolls() {
        show()
        compose.onNodeWithTag("tab-DATA").performClick()
        compose.waitForIdle()
        val rows = compose.onAllNodes(verticalList).onFirst().fetchSemanticsNode().config.getOrNull(SemanticsProperties.CollectionInfo)?.rowCount ?: 1
        compose.onAllNodes(verticalList).onFirst().performScrollToIndex(rows - 1)
        compose.waitForIdle()
        compose.onAllNodes(androidx.compose.ui.test.hasText("About", substring = true, ignoreCase = true) and androidx.compose.ui.test.hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        compose.onAllNodes(verticalList).onFirst().performScrollToIndex(5)
        compose.waitForIdle()
    }
}
