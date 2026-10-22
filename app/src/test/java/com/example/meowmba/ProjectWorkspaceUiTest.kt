package com.example.meowmba

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.work.*
import com.example.meowmba.ui.companion.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProjectWorkspaceUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val work = ProjectWorkSnapshot(7, "Meow launch", "OTHER", "Make a clear tester welcome kit",
        notes = "Friendly language. Review before sharing.",
        steps = listOf(ProjectPlanStep("one", "Draft the welcome guide"), ProjectPlanStep("two", "Review the finished files")),
        tasks = listOf(ProjectTaskRecord("task", "Write welcome guide", "file", "file.write", ProjectTaskStatus.COMPLETED, 10, 20, "Saved welcome.md")),
        artifacts = listOf(ProjectArtifact("projects/project-7/documents/welcome.md", "welcome.md", "Documents", 1024, 20, true, true)))
    private val state = CompanionState(activeProjectId = 7, activeProjectName = work.name, projectWork = work,
        projects = listOf(ProjectUi(7, work.name, "OTHER", "Project", "Today", 700)))

    @Test fun dashboardShowsBriefAndRealTaskSuccessWithoutCheckingThePlan() {
        compose.setContent { MaterialTheme { ProjectsScreen(state, {}, {}, {}) } }
        compose.onNodeWithText("Project brief").assertExists()
        compose.onNodeWithText("0 of 2 steps confirmed by you · 1 tool actions succeeded").performScrollTo().assertExists()
        compose.onAllNodes(isToggleable()).onFirst().assertIsOff()
        render("project-workspace-plan")
    }
    @Test fun projectFilesAndArtifactsHaveDirectActions() {
        var files = 0; var opened = ""
        compose.setContent { MaterialTheme { ProjectsScreen(state, {}, {}, {}, onFiles = { files++ }, onArtifact = { opened = it }) } }
        compose.onNodeWithText("Open project files").performClick()
        assertEquals(1, files)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("welcome.md", substring = false))
        compose.onNodeWithText("welcome.md", substring = false).performClick()
        assertEquals(work.artifacts.single().path, opened)
        render("project-workspace-files")
    }
    @Test fun aPlanStepRunsOnlyAfterAnExplicitTapAndCheckingIsSeparate() {
        var step: String? = null; var checked: Pair<String, Boolean>? = null
        compose.setContent { MaterialTheme { ProjectsScreen(state, {}, {}, {}, onContinue = { step = it }, onStepDone = { id, done -> checked = id to done }) } }
        assertNull(step)
        compose.onAllNodesWithText("Work on this step").onFirst().performScrollTo().performClick()
        assertEquals("Draft the welcome guide", step)
        assertNull(checked)
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        assertEquals("one" to true, checked)
    }
    @Test fun newProjectHasAnExplicitBriefAndDoesNotCreateWhenCancelled() {
        var created = 0
        compose.setContent { MaterialTheme { ProjectsScreen(CompanionState(), {}, {}, {}, onCreate = { _, _ -> created++ }) } }
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithText("Create & plan").assertIsNotEnabled()
        compose.onNodeWithText("Project name").performTextInput("Launch")
        compose.onNodeWithText("What do you want to achieve?").performTextInput("A welcome guide")
        compose.onNodeWithText("Create & plan").assertIsEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, created)
    }
    @Test fun restartedWorkIsClearlyInterruptedNotStillRunning() {
        compose.setContent { MaterialTheme { ProjectsScreen(state.copy(projectWork = work.copy(steps = emptyList(),
            tasks = listOf(work.tasks.single().copy(status = ProjectTaskStatus.INTERRUPTED,
                resultSummary = "App restarted. Review before retrying.")))), {}, {}, {}) } }
        compose.onNodeWithText("Interrupted · file.write").performScrollTo().assertExists()
        compose.onNodeWithText("App restarted. Review before retrying.").assertExists()
        compose.onNodeWithText("Stop action").assertDoesNotExist()
    }

    private fun render(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/reports/$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
