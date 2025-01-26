package com.example.shared.domain.usecases.ai

import com.google.ai.client.generativeai.common.ServerException
import com.example.shared.UnableToAssistException
import dev.ai4j.openai4j.OpenAiHttpException
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiChatModelName
import dev.langchain4j.model.output.Response
import timber.log.Timber
import java.time.Duration
import javax.inject.Inject

class OpenAiUseCase @Inject constructor() {

    private val defaultOpenAiOnFailureMessage = "I'm unable to assist with that."
    private val model: OpenAiChatModel = OpenAiChatModel.builder()
        .apiKey("demo")
        .modelName(OpenAiChatModelName.GPT_4_O_MINI).timeout(Duration.ofSeconds(90L))
        .build()

    private fun generateOpenAiSolution(
        userMessage: UserMessage,
        systemInstruction: String
    ): Result<String> {

        try {
            val response: Response<AiMessage> = model.generate(
                SystemMessage.from(systemInstruction),
                userMessage
            )
            val contentText = response.content().text()
            return if (contentText.equals(defaultOpenAiOnFailureMessage))
                Result.failure(UnableToAssistException)
            else
                Result.success(cleanOpenAiResult(contentText))
        } catch (e: ServerException) {
            Timber.d(e)
            return Result.failure(e)
        } catch (e: OpenAiHttpException) {
            Timber.d(e)
            return Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Timber.d(e)
            return Result.failure(e)
        } catch (e: RuntimeException) {
            Timber.d(e)
            return Result.failure(e)
        }
    }

    private fun cleanOpenAiResult(response: String): String {
        return response.replace(
            Regex("""\\\[(.*?)\\]""", RegexOption.DOT_MATCHES_ALL)
        ) { matchResult -> "$$${matchResult.groupValues[1]}$$" }
    }

    /** Generate GPT solution using text and optionally an image */
    fun generateOpenAiSolution(
        fileURL: String?,
        prompt: String,
        systemInstruction: String
    ): Result<String> {

        val userMessage = if (fileURL != null) {
            UserMessage.from(
                TextContent.from(prompt),
                ImageContent.from(fileURL, ImageContent.DetailLevel.HIGH)
            )
        } else {
            UserMessage.from(
                TextContent.from(prompt)
            )
        }

        return generateOpenAiSolution(userMessage, systemInstruction)
    }


}