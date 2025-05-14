package org.example.ai.chatbot.trigger.http;

import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.chatbot.domain.auth.service.IAuthService;
import org.example.ai.chatbot.trigger.http.dto.ChatGPTRequestDTO;
import org.example.ai.chatbot.types.common.Constants;
import org.example.ai.chatbot.types.exception.ChatGPTException;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@RestController()
@CrossOrigin("*")
@RequestMapping("/api/${app.config.api-version}/chatbot/ollama")
@Slf4j
public class OllamaSeriveController {

    @Resource
    private OllamaChatClient ollamaChatClient;

    @Resource
    private IAuthService authService;

    /**
     * http://localhost:8090/api/v0/chatbot/ollama/generate_stream
     */
    @RequestMapping(value = "generate_stream", method = RequestMethod.POST)
    public Flux<String> generateStream(@RequestBody ChatGPTRequestDTO request, @RequestHeader("Authorization") String token, HttpServletResponse response) {
        log.info("trigger generate, request:{}", JSON.toJSONString(request));

        try {
            // 1. Basic configuration: stream output, encoding, disable caching
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");

            // 2. Token verification
            boolean success = authService.checkToken(token);
            if (!success) {
                log.info("Token verification failed");
                return Flux.just(Constants.ResponseCode.TOKEN_ERROR.getCode());
            }

            log.info("Token verification succeeded");

            // 3. Get OpenID
            String openid = authService.openid(token);
            log.info("Processing streaming Q&A request, openid: {} Request model: {}", openid, request.getModel());

            // 4. Convert DTO messages to Spring AI messages
            List<Message> aiMessages = request.getMessages().stream()
                    .map(msg -> {
                        switch (msg.getRole()) {
                            case "user": return new UserMessage(msg.getContent());
                            case "system": return new SystemMessage(msg.getContent());
                            case "assistant": return new AssistantMessage(msg.getContent());
                            default: return new UserMessage(msg.getContent());
                        }
                    })
                    .collect(Collectors.toList());

            // 5. Create options and get model from requestdeepseek-r1:1.5b
            OllamaOptions options = OllamaOptions.create().withModel(request.getModel());

            // 6. Stream the response - extract just the text content from each ChatResponse
            return ollamaChatClient.stream(new Prompt(aiMessages, options))
                    .map(chatResponse -> {
                        if (chatResponse.getResult() != null
                                && chatResponse.getResult().getOutput() != null
                                && chatResponse.getResult().getOutput().getContent() != null) {
                            return chatResponse.getResult().getOutput().getContent();
                        }
                        return "";
                    })
                    .filter(content -> !content.isEmpty() && !content.startsWith("<think>") && !content.startsWith("</think>"));
        } catch (Exception e) {
            log.error("Streaming response, request: {} encountered an exception", request, e);
            return Flux.error(new ChatGPTException(e.getMessage()));
        }
    }
}