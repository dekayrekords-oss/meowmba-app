package com.example.meowmba.core.agent.runtime

import com.example.meowmba.core.agent.tools.ToolDefinition

/** Which prompt dialect to use for on-device tool routing. */
enum class ToolCallPromptMode {
    /** Compact generic JSON contract — best for Gemma/Phi-class instruct models. */
    CONTRACT,
    /** Nous Hermes 3 native function-calling format (<tools>/<tool_call>) —
     *  use when the on-device model is a Hermes fine-tune (e.g.
     *  Hermes-3-Llama-3.2-3B); the model was TRAINED on this exact template. */
    HERMES_NATIVE
}

/**
 * Builds the prompt for on-device tool routing.
 *
 * Small instruct models (2–4B, the class that realistically runs on a phone
 * via MediaPipe) follow short, rigid contracts far better than long system
 * prompts — so the generic contract is deliberately minimal. When the loaded
 * model is a Hermes fine-tune, its own trained function-calling template is
 * used instead. Pure and unit-testable.
 */
object ToolCallPrompt {

    fun build(userText: String, tools: List<ToolDefinition>): String = buildString {
        append("You are a tool router. Reply with ONLY one JSON object, no other text.\n")
        append("Tools:\n")
        tools.forEach { tool ->
            append("- ").append(tool.id).append('(')
            append(tool.inputSchema.entries.joinToString(", ") { it.key })
            append("): ").append(tool.description).append('\n')
        }
        append("Rules:\n")
        append("- Choose at most one tool.\n")
        append("- If the user message is not a tool request, answer {\"tool\": null}.\n")
        append("- Format: {\"tool\": \"<id>\", \"args\": {\"<arg>\": \"<value>\"}}\n")
        append("- Never invent tools or arguments not listed above.\n")
        append('\n')
        append("User: ").append(userText).append('\n')
        append("JSON:")
    }

    /**
     * Hermes 3 native function-calling prompt (ChatML + <tools>/<tool_call>),
     * matching the template Hermes models were trained on. Tool schemas are
     * emitted in OpenAI function format via [HermesProtocol.toOpenAiTools].
     */
    fun buildHermesNative(userText: String, tools: List<ToolDefinition>): String = buildString {
        append("<|im_start|>system\n")
        append("You are a function calling AI model. You are provided with function signatures ")
        append("within <tools></tools> XML tags. You may call one function to assist with the ")
        append("user query. Don't make assumptions about what values to plug into functions. ")
        append("If no tool fits, answer the user normally without a tool call. ")
        append("Here are the available tools: <tools>")
        append(HermesProtocol.toOpenAiTools(tools).toString())
        append("</tools> For each function call return a json object with function name and ")
        append("arguments within <tool_call></tool_call> XML tags as follows:\n")
        append("<tool_call>\n{\"arguments\": <args-dict>, \"name\": <function-name>}\n</tool_call>")
        append("<|im_end|>\n")
        append("<|im_start|>user\n").append(userText).append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }
}
