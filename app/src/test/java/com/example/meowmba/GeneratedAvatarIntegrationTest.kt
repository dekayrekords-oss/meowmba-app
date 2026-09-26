package com.example.meowmba

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.geometry.Offset
import com.example.meowmba.core.agent.ModelProvider
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.runtime.AgentRouter
import com.example.meowmba.core.agent.tools.AvatarFormTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class GeneratedAvatarIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Provider(private val answer: suspend () -> ModelResponse) : ModelProvider {
        override val name = "Test configured model"
        override suspend fun generate(prompt: String) = answer()
        override fun generateStreaming(prompt: String): Flow<ModelResponse> = flowOf(ModelResponse.Error("Use structured generation"))
    }

    private suspend fun TestScope.fixture(queued: Boolean = false, block: suspend Fixture.() -> Unit) {
        val dispatcher = if (queued) StandardTestDispatcher(testScheduler) else UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val previousIdle = System.getProperty("meowmba.idleAnimations")
        System.setProperty("meowmba.idleAnimations", "false")
        val android = ScopedCompanionAndroidMocks { testScheduler.currentTime }
        val fixture = Fixture(dispatcher, temporary.newFolder())
        try { fixture.block() } finally {
            fixture.models.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
            android.close()
            Dispatchers.resetMain()
            if (previousIdle == null) System.clearProperty("meowmba.idleAnimations") else System.setProperty("meowmba.idleAnimations", previousIdle)
        }
    }

    private class Fixture(val dispatcher: TestDispatcher, val folder: File) {
        val values = mutableMapOf<String, String>()
        val models = mutableListOf<CompanionViewModel>()
        val store = GeneratedAvatarStore(File(folder, "avatar_forms"))
        val storage = mock(SecureStorageManager::class.java)
        init {
            val context = mock(Context::class.java)
            `when`(context.filesDir).thenReturn(folder)
            `when`(storage.context).thenReturn(context)
            `when`(storage.getPreference(anyString())).thenAnswer { values[it.getArgument<String>(0)] }
            doAnswer { values[it.getArgument<String>(0)] = it.getArgument(1); null }
                .`when`(storage).savePreference(anyString(), anyString())
            `when`(storage.getBooleanPreference(anyString(), anyBoolean())).thenAnswer {
                values[it.getArgument<String>(0)]?.toBooleanStrictOrNull() ?: it.getArgument<Boolean>(1)
            }
            doAnswer { values[it.getArgument<String>(0)] = it.getArgument<Boolean>(1).toString(); null }
                .`when`(storage).saveBooleanPreference(anyString(), anyBoolean())
            doAnswer { values["avatar_animal"] = it.getArgument(0); values["avatar_style"] = "lumen";
                values.remove("generated_avatar_id"); null }.`when`(storage).saveAvatarForm(anyString())
            doAnswer { values["generated_avatar_id"] = it.getArgument(0); values["avatar_style"] = "lumen"; null }
                .`when`(storage).saveGeneratedAvatarForm(anyString())
        }
        fun model() = CompanionViewModel(secureStorage = storage,
            workspaceRoot = File(folder, "workspace"),
            ttsManager = VoicePipelineTest.FakeSpeechSynthesizer(), ioDispatcher = dispatcher).also { models += it }
    }

    @Test fun `requested custom form is applied saved and restored with same companion preferences`() = runTest {
        fixture {
            val form = GeneratedAvatarExamples.dragon()
            val model = model()
            model.setAvatarAnimal(AvatarAnimal.CLIPPY)
            model.setAvatarScale(2.6f)
            model.setHabitatMode(true)
            model.currentProvider = Provider { ModelResponse.Complete(GeneratedAvatarForms.toJson(form)) }
            val result = model.generateAvatarForm("a turquoise dragon with peach wings")
            assertTrue(result is ToolResult.Success)
            val state = model.uiState.value
            assertEquals(form, state.generatedAvatarForm)
            assertEquals(AvatarStyle.LUMEN, state.avatarStyle)
            assertEquals(AvatarAnimal.CLIPPY, state.avatarAnimal)
            assertEquals(2.6f, state.avatarScale, 0f)
            assertTrue(state.habitatMode)
            assertFalse(state.isCameraEnabled)
            assertFalse(state.avatarFormGenerating)
            assertEquals(form.id, values["generated_avatar_id"])
            assertEquals(form, model().uiState.value.generatedAvatarForm)
            assertEquals(1, store.list().size)
        }
    }

    @Test fun `saved phrase and chosen library form work without a model and can be removed`() = runTest {
        fixture {
            val form = GeneratedAvatarExamples.coffeeCup()
            store.save(form, "walking coffee cup")
            val model = model().also { it.currentProvider = null }
            assertTrue(model.generateAvatarForm("walking coffee cup") is ToolResult.Success)
            model.setAvatarAnimal(AvatarAnimal.WIZARD)
            assertNull(values["generated_avatar_id"])
            model.selectGeneratedAvatarForm(form.id)
            assertEquals(form, model.uiState.value.generatedAvatarForm)
            model.deleteGeneratedAvatarForm(form.id)
            assertNull(model.uiState.value.generatedAvatarForm)
            assertEquals(AvatarAnimal.WIZARD, model.uiState.value.avatarAnimal)
            assertTrue(store.list().isEmpty())
        }
    }

    @Test fun `missing or invalid model output leaves selected form and privacy untouched`() = runTest {
        fixture {
            val model = model()
            model.setAvatarAnimal(AvatarAnimal.FRUIT_FLY)
            model.currentProvider = null
            assertTrue(model.generateAvatarForm("a purple toaster") is ToolResult.Failure)
            assertTrue(model.uiState.value.avatarFormError)
            var calls = 0
            model.currentProvider = Provider { calls++; ModelResponse.Complete("{\"code\":\"doThings()\"}") }
            assertTrue(model.generateAvatarForm("a purple toaster") is ToolResult.Failure)
            assertEquals(2, calls)
            assertEquals(AvatarAnimal.FRUIT_FLY, model.uiState.value.avatarAnimal)
            assertNull(model.uiState.value.generatedAvatarForm)
            assertTrue(store.list().isEmpty())
            assertFalse(model.uiState.value.isCameraEnabled)
            assertFalse(model.uiState.value.avatarFormGenerating)
        }
    }

    @Test fun `equipping a prop preserves a generated avatar and survives restart until put away`() = runTest {
        fixture {
            val form = GeneratedAvatarExamples.dragon()
            store.save(form, "dragon")
            val props = GeneratedPropStore(File(folder, "companion_props"))
            val prop = GeneratedPropForms.parse(GeneratedPropForms.toJson(GeneratedPropForm(
                "", "Coffee", "A small cup", GeneratedPropCategory.HELD, "Hold it",
                GeneratedAvatarExamples.coffeeCup().parts.take(2))))
            props.save(prop, "cup")
            val model = model()
            model.selectGeneratedAvatarForm(form.id)
            model.equipProp(prop.id)
            assertEquals(form, model.uiState.value.generatedAvatarForm)
            assertEquals(prop, model.uiState.value.equippedProp)
            assertEquals(prop.id, values["equipped_prop_id"])
            val restored = model()
            assertEquals(form, restored.uiState.value.generatedAvatarForm)
            assertEquals(prop, restored.uiState.value.equippedProp)
            restored.equipProp("missing")
            assertTrue(restored.uiState.value.propInventError)
            assertEquals(prop, restored.uiState.value.equippedProp)
            restored.equipProp(null)
            assertNull(model().uiState.value.equippedProp)
            assertEquals(form, restored.uiState.value.generatedAvatarForm)
            restored.equipProp(prop.id)
            restored.deleteProp(prop.id)
            assertNull(restored.uiState.value.equippedProp)
            assertNull(model().uiState.value.equippedProp)
            assertNull(props.load(prop.id))
        }
    }

    @Test fun `a later preset cancels even a noncooperative model without a late save`() = runTest {
        fixture {
            val model = model()
            val released = CompletableDeferred<Unit>()
            model.currentProvider = Provider {
                withContext(NonCancellable) { released.await() }
                ModelResponse.Complete(GeneratedAvatarForms.toJson(GeneratedAvatarExamples.dragon()))
            }
            val work = async(dispatcher) { model.generateAvatarForm("a blue dragon") }
            assertTrue(model.uiState.value.avatarFormGenerating)
            model.setAvatarAnimal(AvatarAnimal.CLIPPY)
            released.complete(Unit)
            runCatching { work.await() }
            assertTrue(work.isCancelled)
            assertEquals(AvatarAnimal.CLIPPY, model.uiState.value.avatarAnimal)
            assertNull(model.uiState.value.generatedAvatarForm)
            assertTrue(store.list().isEmpty())
            assertFalse(model.uiState.value.avatarFormGenerating)
        }
    }

    @Test fun `optional review timeout preserves completed avatar and prop designs`() = runTest {
        fixture {
            val model = model()
            val form = GeneratedAvatarExamples.coffeeCup()
            var calls = 0
            model.currentProvider = Provider {
                if (++calls == 1) ModelResponse.Complete(GeneratedAvatarForms.toJson(form)) else awaitCancellation()
            }
            val work = async(dispatcher) { model.generateAvatarForm("a dancing cup", reasonLevel = InventReasonLevel.BALANCED) }
            advanceTimeBy(90_001); runCurrent()
            assertTrue(work.await() is ToolResult.Success)
            assertEquals(form, model.uiState.value.generatedAvatarForm)
            assertTrue(model.uiState.value.avatarFormMessage.orEmpty().contains("Extra polish timed out"))
            assertFalse(model.uiState.value.avatarFormGenerating)

            val prop = GeneratedPropForms.parse(GeneratedPropForms.toJson(GeneratedPropForm(
                "", "Cup", "", GeneratedPropCategory.HELD, "Hold it", form.parts.take(2))))
            calls = 0
            model.currentProvider = Provider {
                if (++calls == 1) ModelResponse.Complete(GeneratedPropForms.toJson(prop)) else awaitCancellation()
            }
            model.inventProp("a blue cup", "held", "", InventReasonLevel.BALANCED)
            advanceTimeBy(75_001); runCurrent()
            val placedProp = GeneratedPropForms.withSmartDefaults(prop)
            assertEquals(placedProp, model.uiState.value.savedProps.single())
            assertEquals(placedProp, model.uiState.value.pendingPropPlacement)
            assertFalse(model.uiState.value.propInventError)
            assertFalse(model.uiState.value.propInventGenerating)
            assertTrue(model.uiState.value.propInventMessage.orEmpty().contains("Extra polish timed out"))
        }
    }

    @Test fun `user cancellation during optional polish never saves the candidate`() = runTest {
        fixture {
            val model = model()
            val form = GeneratedAvatarExamples.coffeeCup()
            var calls = 0
            model.currentProvider = Provider {
                if (++calls == 1) ModelResponse.Complete(GeneratedAvatarForms.toJson(form)) else awaitCancellation()
            }
            val work = async(dispatcher) { model.generateAvatarForm("a dancing cup", reasonLevel = InventReasonLevel.BALANCED) }
            assertEquals(2, calls)
            model.setAvatarAnimal(AvatarAnimal.CLIPPY)
            runCatching { work.await() }
            assertTrue(work.isCancelled)
            assertTrue(store.list().isEmpty())
            assertEquals(AvatarAnimal.CLIPPY, model.uiState.value.avatarAnimal)

            val prop = GeneratedPropForms.parse(GeneratedPropForms.toJson(GeneratedPropForm(
                "", "Cup", "", GeneratedPropCategory.HELD, "Hold it", form.parts.take(2))))
            calls = 0
            model.currentProvider = Provider {
                if (++calls == 1) ModelResponse.Complete(GeneratedPropForms.toJson(prop)) else awaitCancellation()
            }
            model.inventProp("a blue cup", "held", "", InventReasonLevel.BALANCED)
            assertEquals(2, calls)
            model.cancelPropInvent()
            advanceTimeBy(75_001); runCurrent()
            assertTrue(model.uiState.value.savedProps.isEmpty())
            assertFalse(model.uiState.value.propInventGenerating)
        }
    }

    @Test fun `newest generation wins and leaving the foreground discards unfinished work`() = runTest {
        fixture {
            val model = model()
            val releaseOld = CompletableDeferred<Unit>()
            model.currentProvider = Provider {
                withContext(NonCancellable) { releaseOld.await() }
                ModelResponse.Complete(GeneratedAvatarForms.toJson(GeneratedAvatarExamples.dragon()))
            }
            val old = async(dispatcher) { model.generateAvatarForm("a dragon") }
            model.currentProvider = Provider { ModelResponse.Complete(GeneratedAvatarForms.toJson(GeneratedAvatarExamples.spaceship())) }
            assertTrue(model.generateAvatarForm("a tiny spaceship") is ToolResult.Success)
            releaseOld.complete(Unit)
            runCatching { old.await() }
            assertEquals(GeneratedAvatarExamples.spaceship().id, model.uiState.value.generatedAvatarForm?.id)
            assertEquals(1, store.list().size)
            model.currentProvider = Provider { delay(5_000); ModelResponse.Complete(GeneratedAvatarForms.toJson(GeneratedAvatarExamples.dragon())) }
            val backgrounded = async(dispatcher) { model.generateAvatarForm("a red dragon") }
            model.releaseWakeMicForBackground()
            runCatching { backgrounded.await() }
            assertTrue(backgrounded.isCancelled)
            assertEquals(1, store.list().size)
        }
    }

    @Test fun `tool routing preserves the full request and cancelled tokens never invoke a preset`() = runTest {
        val route = requireNotNull(AgentRouter.route("turn into a turquoise dragon with peach wings"))
        assertEquals("turquoise dragon with peach wings", route.args["animal"])
        var changed = false
        val tool = AvatarFormTool(onChange = { changed = true }, onGenerate = { _, _, _ -> ToolResult.Success("ok") },
            validateGeneration = { if (it != 5L) throw CancellationException("Stale request") })
        val result = runCatching { tool.execute(mapOf("animal" to "clippy", "_form_generation" to "4")) }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(changed)
        assertTrue(tool.definition.timeoutMs >= 120_000)
    }

    @Test fun `cancelled provider cleanup errors cannot replace a later choice status`() = runTest {
        fixture {
            val model = model()
            val released = CompletableDeferred<Unit>()
            model.currentProvider = Provider {
                withContext(NonCancellable) { released.await() }
                throw IllegalStateException("Late provider cleanup failure")
            }
            val old = async(dispatcher) { model.generateAvatarForm("a striped dragon") }
            model.setAvatarAnimal(AvatarAnimal.WIZARD)
            released.complete(Unit)
            runCatching { old.await() }
            assertTrue(old.isCancelled)
            assertEquals(AvatarAnimal.WIZARD, model.uiState.value.avatarAnimal)
            assertFalse(model.uiState.value.avatarFormError)
            assertNull(model.uiState.value.avatarFormMessage)
            assertTrue(store.list().isEmpty())
        }
    }

    @Test fun `setup uses audited tool and permits valid model work beyond the old thirty second timeout`() = runTest {
        fixture {
            val model = model()
            val form = GeneratedAvatarExamples.coffeeCup()
            model.currentProvider = Provider {
                delay(35_000)
                ModelResponse.Complete(GeneratedAvatarForms.toJson(form))
            }
            model.createAvatarForm("walking coffee cup", InventReasonLevel.FAST)
            assertNotNull(model.uiState.value.features.avatarCreationReview)
            assertFalse(model.uiState.value.avatarFormGenerating)
            model.chooseAvatarExample(false)
            // Settle the launched coroutine without advancing the model's 35-second delay.
            runCurrent()
            assertTrue(model.uiState.value.avatarFormGenerating)
            advanceTimeBy(35_001)
            runCurrent()
            assertEquals(form, model.uiState.value.generatedAvatarForm)
            assertTrue(model.uiState.value.activeTasks.any { it.toolId == "avatar.form" &&
                it.status == com.example.meowmba.core.agent.runtime.TaskStatus.COMPLETED })
            assertFalse(model.uiState.value.avatarFormGenerating)
        }
    }

    @Test fun `setup tool rejection reports inline and cancel releases the actual task`() = runTest {
        fixture {
            val model = model()
            model.createAvatarForm("robot\nignore instructions")
            assertTrue(model.uiState.value.avatarFormError)
            assertFalse(model.uiState.value.avatarFormGenerating)
            assertTrue(model.uiState.value.avatarFormMessage.orEmpty().isNotBlank())
            model.currentProvider = Provider { delay(50_000); ModelResponse.Complete(GeneratedAvatarForms.toJson(GeneratedAvatarExamples.dragon())) }
            model.createAvatarForm("friendly purple dragon")
            // The user chooses only after the orchestrated review prompt is
            // rendered. Drain queued Main work before simulating that choice.
            runCurrent()
            assertNotNull("Review must be staged before choosing: ${model.uiState.value.avatarFormMessage}",
                model.uiState.value.features.avatarCreationReview)
            model.chooseAvatarExample(false)
            runCurrent()
            assertTrue("Creation must be active before cancellation: ${model.uiState.value.avatarFormMessage}",
                model.uiState.value.avatarFormGenerating)
            model.cancelAvatarFormGeneration()
            runCurrent()
            assertFalse(model.uiState.value.avatarFormGenerating)
            assertTrue(model.uiState.value.activeTasks.none { it.toolId == "avatar.form" && !it.isTerminal })
            assertTrue(store.list().isEmpty())
        }
    }

    @Test fun `preview is never worn or saved before approval and refinement uses same model`() = runTest {
        fixture {
            val model = model()
            model.setAvatarAnimal(AvatarAnimal.CAT)
            val form = GeneratedAvatarExamples.dragon()
            model.currentProvider = Provider { ModelResponse.Complete(GeneratedAvatarForms.toJson(form)) }
            model.requestAvatarExample("friendly purple dragon", InventReasonLevel.FAST)
            assertNull(model.uiState.value.generatedAvatarForm)
            model.chooseAvatarExample(true)
            runCurrent()
            assertEquals(form, model.uiState.value.features.avatarCreationReview?.candidate)
            assertNull(model.uiState.value.generatedAvatarForm)
            assertTrue(store.list().isEmpty())
            model.refineAvatarExample("make it blue")
            runCurrent()
            assertFalse(model.uiState.value.avatarFormError)
            model.acceptAvatarExample()
            assertEquals(form, model.uiState.value.generatedAvatarForm)
            assertEquals(1, store.list().size)
            assertNull(model.uiState.value.features.avatarCreationReview)
        }
    }

    @Test fun `cancel before queued example launch prevents the model call and save`() = runTest {
        for (show in listOf(false, true)) fixture(queued = true) {
            val model = model()
            var calls = 0
            model.currentProvider = Provider { calls++; ModelResponse.Complete(GeneratedAvatarForms.toJson(GeneratedAvatarExamples.coffeeCup())) }
            model.requestAvatarExample("a walking coffee cup", InventReasonLevel.FAST)
            model.chooseAvatarExample(show)
            model.cancelAvatarFormGeneration()
            runCurrent()
            assertEquals(0, calls)
            assertFalse(model.uiState.value.avatarFormGenerating)
            assertNull(model.uiState.value.generatedAvatarForm)
            assertTrue(store.list().isEmpty())
        }
    }

    @Test fun `cancel before queued preview refinement preserves the candidate without calling the model`() = runTest {
        fixture(queued = true) {
            val model = model()
            val form = GeneratedAvatarExamples.coffeeCup()
            var calls = 0
            model.currentProvider = Provider { calls++; ModelResponse.Complete(GeneratedAvatarForms.toJson(form)) }
            model.requestAvatarExample("a walking coffee cup", InventReasonLevel.FAST)
            model.chooseAvatarExample(true)
            runCurrent()
            assertEquals(form, model.uiState.value.features.avatarCreationReview?.candidate)
            assertEquals(1, calls)
            assertNull(model.uiState.value.generatedAvatarForm)
            model.refineAvatarExample("give it small wings")
            model.cancelAvatarFormGeneration()
            runCurrent()
            assertEquals(1, calls)
            assertEquals(form, model.uiState.value.features.avatarCreationReview?.candidate)
            assertNull(model.uiState.value.generatedAvatarForm)
            assertFalse(model.uiState.value.avatarFormGenerating)
            assertTrue(store.list().isEmpty())
        }
    }

    @Test fun `cancel before queued worn avatar refinement preserves the saved form`() = runTest {
        fixture(queued = true) {
            val model = model()
            val form = GeneratedAvatarExamples.coffeeCup()
            var calls = 0
            model.currentProvider = Provider { calls++; ModelResponse.Complete(GeneratedAvatarForms.toJson(form)) }
            assertTrue(model.generateAvatarForm("a walking coffee cup", reasonLevel = InventReasonLevel.FAST) is ToolResult.Success)
            assertEquals(1, calls)
            model.refineAvatarForm("give it small wings", InventReasonLevel.FAST)
            model.cancelAvatarFormGeneration()
            runCurrent()
            assertEquals(1, calls)
            assertEquals(form, model.uiState.value.generatedAvatarForm)
            assertFalse(model.uiState.value.avatarFormGenerating)
            assertEquals(1, store.list().size)
        }
    }

    @Test fun `failed preview can retry in place without saving or changing the avatar`() = runTest {
        fixture {
            val model = model()
            val form = GeneratedAvatarExamples.dragon()
            var calls = 0
            model.currentProvider = Provider {
                calls++
                if (calls == 1) ModelResponse.Error("HTTP 401 secret-provider-detail")
                else ModelResponse.Complete(GeneratedAvatarForms.toJson(form))
            }
            model.requestAvatarExample("friendly purple dragon", InventReasonLevel.FAST)
            model.chooseAvatarExample(true)
            runCurrent()
            assertTrue(model.uiState.value.avatarFormError)
            assertFalse(model.uiState.value.avatarFormMessage.orEmpty().contains("secret-provider-detail"))
            assertNull(model.uiState.value.features.avatarCreationReview?.candidate)
            model.chooseAvatarExample(true)
            runCurrent()
            assertEquals(2, calls)
            assertEquals(form, model.uiState.value.features.avatarCreationReview?.candidate)
            assertNull(model.uiState.value.generatedAvatarForm)
            assertTrue(store.list().isEmpty())
            model.chooseAvatarExample(true)
            assertEquals("A ready candidate is not regenerated by a repeated click", 2, calls)
        }
    }

    @Test fun `optional review failure visibly retains a real completed design`() = runTest {
        fixture {
            val model = model()
            val form = GeneratedAvatarExamples.dragon()
            var calls = 0
            model.currentProvider = Provider {
                calls++
                if (calls == 1) ModelResponse.Complete(GeneratedAvatarForms.toJson(form))
                else ModelResponse.Error("private provider problem")
            }
            model.requestAvatarExample("friendly purple dragon", InventReasonLevel.BALANCED)
            model.chooseAvatarExample(true)
            runCurrent()
            assertEquals(form, model.uiState.value.features.avatarCreationReview?.candidate)
            assertTrue(model.uiState.value.avatarFormMessage.orEmpty().contains("polish"))
            assertFalse(model.uiState.value.avatarFormMessage.orEmpty().contains("private provider"))
            assertFalse(model.uiState.value.avatarFormError)
            assertTrue(store.list().isEmpty())
            model.acceptAvatarExample()
            assertEquals(form, model.uiState.value.generatedAvatarForm)
        }
    }

    @Test fun `outer task timeout never saves a candidate completed before optional polish`() = runTest {
        fixture {
            val model = model()
            val form = GeneratedAvatarExamples.dragon()
            var calls = 0
            model.currentProvider = Provider {
                calls++
                if (calls == 1) ModelResponse.Complete(GeneratedAvatarForms.toJson(form))
                else { delay(60_000); ModelResponse.Complete(GeneratedAvatarForms.toJson(form)) }
            }
            val result = runCatching {
                withTimeout(2_000) { model.generateAvatarForm("friendly purple dragon", reasonLevel = InventReasonLevel.BALANCED, createNew = true) }
            }
            assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
            assertTrue(store.list().isEmpty())
            assertNull(model.uiState.value.generatedAvatarForm)
            assertFalse(model.uiState.value.avatarFormGenerating)
        }
    }

    @Test fun `creation shows elapsed progress and cancellation stops progress updates`() = runTest {
        fixture {
            val model = model()
            model.currentProvider = Provider { delay(60_000); ModelResponse.Complete("{}") }
            model.requestAvatarExample("friendly purple dragon", InventReasonLevel.FAST)
            model.chooseAvatarExample(true)
            advanceTimeBy(10_001)
            runCurrent()
            assertTrue(model.uiState.value.avatarFormMessage.orEmpty().contains("10s"))
            model.cancelAvatarFormGeneration()
            runCurrent()
            val cancelled = model.uiState.value.avatarFormMessage
            advanceTimeBy(15_000)
            runCurrent()
            assertEquals(cancelled, model.uiState.value.avatarFormMessage)
            assertFalse(model.uiState.value.avatarFormGenerating)
            assertTrue(store.list().isEmpty())
        }
    }

    @Test fun `dismissed preview and home control preferences preserve main avatar and privacy`() = runTest {
        fixture {
            val model = model()
            model.requestAvatarExample("purple dragon", InventReasonLevel.FAST)
            model.dismissAvatarReview()
            model.chooseAvatarExample(false)
            assertTrue(store.list().isEmpty())
            model.setHomeControls(false, "Bottom right", false)
            val restored = model().uiState.value
            assertFalse(restored.features.careVisible)
            assertFalse(restored.features.neuralFeedbackVisible)
            assertEquals("Bottom right", restored.features.carePosition)
            assertFalse(restored.isCameraEnabled)
        }
    }

    @Test fun `vending animation uses configured model and cancellation stops actual work`() = runTest {
        fixture {
            val model = model()
            var cancelled = false
            var calls = 0
            model.currentProvider = Provider {
                calls++
                try { delay(40_000); ModelResponse.Complete(FishingFixture.json) }
                finally { cancelled = true }
            }
            model.inventAnimationFromVending("fishing")
            runCurrent()
            assertEquals(1, calls)
            assertTrue(model.uiState.value.animInventGenerating)
            model.cancelAnimInvent()
            runCurrent()
            assertTrue(cancelled)
            assertFalse(model.uiState.value.animInventGenerating)
            assertTrue(CustomAnimationStore(File(folder, "custom_animations")).list().isEmpty())
        }
    }

    @Test fun `reference requires analysis and unapproved previews cannot replace a named selection`() = runTest {
        fixture {
            val model = model()
            model.setCreationReference("content://reference/image")
            model.createAvatarForm("purple dragon")
            assertTrue(model.uiState.value.avatarFormError)
            assertNull(model.uiState.value.features.avatarCreationReview)
            model.setCreationReference(null)
            model.requestAvatarExample("purple dragon", InventReasonLevel.FAST)
            model.setAvatarStyle(AvatarStyle.SIGNAL)
            model.acceptAvatarExample()
            assertEquals(AvatarStyle.SIGNAL, model.uiState.value.avatarStyle)
            assertNull(model.uiState.value.features.avatarCreationReview)
        }
    }

    @Test fun `generated hit placement tracks zoom and habitat position in both orientations`() {
        for ((w, h) in listOf(360f to 800f, 840f to 360f)) for (scale in listOf(.5f, 2f, 3f)) {
            for (pose in listOf(null, FullFlyPose(x = .75f, y = .4f))) {
                val placement = generatedAvatarPlacement(w, h, LumenFrame(), scale, pose)
                assertTrue(placement.owns(placement.center))
                assertTrue(placement.owns(placement.center + Offset(placement.unit * .55f, 0f)))
                assertFalse(placement.owns(placement.center + Offset(placement.unit, placement.unit)))
                assertFalse(placement.owns(Offset(Float.NaN, 0f)))
                assertTrue(placement.center.x in 0f..w && placement.center.y in 0f..h)
            }
        }
    }
}
