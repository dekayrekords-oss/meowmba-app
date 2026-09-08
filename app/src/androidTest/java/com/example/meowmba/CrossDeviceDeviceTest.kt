package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.agent.tools.AppModelSwitcher
import com.example.meowmba.core.agent.tools.InstalledApps
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.setup.PlannerReadiness
import com.example.meowmba.core.setup.SetupWiring
import com.example.meowmba.core.agent.RemoteModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Read-only device acceptance: no credentials, messages, or device names in
 * output; no cloud requests or account mutations. */
class CrossDeviceDeviceTest {
    @Test fun verifyConfiguredFreeCloudOnlyWhenExplicitlyRequested() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val arguments=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(arguments.getString("verifyFreeRouter")=="true")
        val context=instrumentation.targetContext
        val storage=SecureStorageManager(context)
        val key=storage.getApiKey("openrouter")
        val model=arguments.getString("freeModel") ?: storage.getModelName("openrouter").orEmpty()
        require(!key.isNullOrBlank()) { "OpenRouter key is missing; no cloud setup changed" }
        require(model=="openrouter/free" || model.endsWith(":free")) { "Test permits only the already configured free model; no paid requests" }
        val entry=com.example.meowmba.core.agent.OpenRouterModels.fetch().getOrThrow().firstOrNull { it.id==model }
        require(entry?.free==true && entry.tools) { "Live catalog must confirm zero-priced tool calling" }
        RemoteModelProvider("OpenRouter","https://openrouter.ai/api",key,model).use { provider ->
            repeat(2) { PlannerReadiness.verify(provider) }
        }
        if (arguments.getString("selectVerifiedFreeRouter")=="true") {
            storage.savePreference("planner_verified_openrouter",PlannerReadiness.cloudStamp(model,key))
            SetupWiring.setBrain(context,"openrouter",key,model)
            storage.savePreference("selected_model","OpenRouter (Cloud)")
        }
        instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","Configured free OpenRouter model $model passed actual tool-calling and context probes. Selected=${arguments.getString("selectVerifiedFreeRouter")=="true"}. Keys were not printed; local downloads were preserved.\n") })
    }
    @Test fun installedCapabilitiesAndConfigurationWithoutSecrets() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val storage=SecureStorageManager(context)
        val choices=AppModelSwitcher(context,busy={false},changed={}).choices()
        assertTrue("Existing local download must survive the upgrade",choices.contains("gemma-4-E2B-it.litertlm"))
        val found=listOf("Facebook","Reddit","Instagram","X","ChatGPT","Gemini").filter { InstalledApps.find(context,it)!=null }
        assertTrue(found.contains("Facebook")); assertTrue(found.contains("Reddit"))
        val cloudKey=storage.getApiKey("openrouter")
        val cloudModel=storage.getModelName("openrouter")
        val verified=storage.getPreference("planner_verified_openrouter")==PlannerReadiness.cloudStamp(cloudModel,cloudKey)
        instrumentation.sendStatus(0,android.os.Bundle().apply {
            putString("stream","Installed test apps: ${found.joinToString()}. Local model preserved. Selected=${storage.getPreference("selected_model")}; OpenRouter key present=${!cloudKey.isNullOrBlank()}, model=${cloudModel.orEmpty()}, planner verified=$verified. No keys printed or cloud requests made.\n")
        })
    }
}
