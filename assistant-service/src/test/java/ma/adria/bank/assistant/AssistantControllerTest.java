package ma.adria.bank.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssistantControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @MockitoBean
    ChatModel chatModel;

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private String chat(String message, String conversationId) throws Exception {
        Map<String, String> body = conversationId == null
                ? Map.of("message", message) : Map.of("message", message, "conversationId", conversationId);
        return mvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void returnsTheModelReplyAndAConversationId() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(answer("Vous avez 3 comptes."));

        mvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Combien de comptes ?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Vous avez 3 comptes."))
                .andExpect(jsonPath("$.conversationId").isNotEmpty());
    }

    @Test
    void sendsTheBankingSystemPromptAndUserMessageToTheModel() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(answer("ok"));

        chat("Quel est le solde de CUST-001 ?", null);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(captor.capture());
        List<Message> messages = captor.getValue().getInstructions();
        assertThat(messages).anySatisfy(m -> {
            assertThat(m.getMessageType()).isEqualTo(MessageType.SYSTEM);
            assertThat(m.getText()).contains("n'invente jamais").contains("confirmation explicite");
        });
        assertThat(messages).anySatisfy(m -> {
            assertThat(m.getMessageType()).isEqualTo(MessageType.USER);
            assertThat(m.getText()).isEqualTo("Quel est le solde de CUST-001 ?");
        });
    }

    @Test
    void sameConversationKeepsHistoryAndAnotherDoesNot() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenReturn(answer("réponse"));

        chat("Je m'appelle Maroua", "conv-A");
        chat("Comment je m'appelle ?", "conv-A");
        chat("Bonjour", "conv-B");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, atLeastOnce()).call(captor.capture());
        List<Prompt> prompts = captor.getAllValues();
        Prompt second = prompts.get(prompts.size() - 2);
        Prompt third = prompts.get(prompts.size() - 1);
        assertThat(second.getInstructions()).extracting(Message::getText).contains("Je m'appelle Maroua");
        assertThat(third.getInstructions()).extracting(Message::getText).doesNotContain("Je m'appelle Maroua");
    }

    @Test
    void blankMessageIsRejected() throws Exception {
        mvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modelFailureReturns503() throws Exception {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("Ollama injoignable"));

        mvc.perform(post("/api/assistant/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Bonjour\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
