package com.example.meowmba

import com.example.meowmba.core.agent.OpenRouterModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterModelsTest {

    @Test
    fun `parses chat and vision models and skips embeddings`() {
        val models = OpenRouterModels.parse(
            """
            {"data":[
              {"id":"nvidia/nemotron-3-super-120b-a12b:free","name":"Nemotron","architecture":{"input_modalities":["text"],"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0"}},
              {"id":"google/gemini-2.5-flash","name":"Gemini 2.5 Flash","architecture":{"input_modalities":["text","image"],"output_modalities":["text"]},"pricing":{"prompt":"0.0000003","completion":"0.0000025"}},
              {"id":"vendor/embed-v1","name":"Embed","architecture":{"input_modalities":["text"],"output_modalities":["embeddings"]},"pricing":{"prompt":"0.0001","completion":"0"}}
            ]}
            """.trimIndent()
        )
        assertEquals(2, models.size)
        assertEquals("nvidia/nemotron-3-super-120b-a12b:free", models[0].id)
        assertTrue(models[0].free)
        assertTrue(!models[0].vision)
        val gemini = models.first { it.id.contains("gemini") }
        assertTrue(gemini.vision)
        assertTrue(!gemini.free)
    }
}
