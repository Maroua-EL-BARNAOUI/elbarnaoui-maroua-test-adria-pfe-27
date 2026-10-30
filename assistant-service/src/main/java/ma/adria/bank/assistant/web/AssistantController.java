package ma.adria.bank.assistant.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.adria.bank.assistant.service.AssistantService;
import ma.adria.bank.assistant.service.AssistantService.ChatReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public ChatReply chat(@Valid @RequestBody ChatRequest request) {
        return assistantService.chat(request.message(), request.conversationId());
    }

    @ExceptionHandler(RuntimeException.class)
    ProblemDetail llmFailure(RuntimeException ex) {
        log.error("Échec de l'assistant", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "L'assistant est momentanément indisponible (modèle ou outils injoignables)");
    }

    public record ChatRequest(@NotBlank @Size(max = 2000) String message, String conversationId) {
    }
}
