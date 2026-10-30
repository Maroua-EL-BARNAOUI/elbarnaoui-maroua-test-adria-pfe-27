package ma.adria.bank.assistant.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Agent bancaire : le LLM choisit lui-même les outils MCP exposés par account-service et
 * transaction-service (consultation des comptes, paiements, virements) pour répondre.
 */
@Service
public class AssistantService {

    static final String SYSTEM_PROMPT = """
            Tu es l'assistant d'une banque. Tu réponds en français, de façon concise.
            Pour toute information sur les comptes ou les transactions, utilise TOUJOURS les outils
            disponibles : n'invente jamais un solde, un identifiant ou un montant.
            Les identifiants de comptes sont des UUID : si l'utilisateur donne une référence client
            (ex : CUST-001), retrouve d'abord ses comptes avec l'outil adapté.
            Avant d'effectuer un paiement ou un virement, récapitule l'opération (comptes, montant,
            devise) et demande une confirmation explicite, sauf si l'utilisateur l'a déjà confirmée.
            Si un outil renvoie une erreur, explique-la simplement sans la masquer.
            """;

    private final ChatClient chatClient;

    public AssistantService(ChatModel chatModel, ChatMemory chatMemory,
                            ObjectProvider<ToolCallbackProvider> toolProviders) {
        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build());
        ToolCallbackProvider[] providers = toolProviders.orderedStream().toArray(ToolCallbackProvider[]::new);
        if (providers.length > 0) {
            builder.defaultToolCallbacks(providers);
        }
        this.chatClient = builder.build();
    }

    /** Une conversation conserve son historique grâce à son identifiant. */
    public ChatReply chat(String message, String conversationId) {
        String id = conversationId == null || conversationId.isBlank() ? UUID.randomUUID().toString() : conversationId;
        String reply = chatClient.prompt()
                .user(message)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, id))
                .call()
                .content();
        return new ChatReply(id, reply);
    }

    public record ChatReply(String conversationId, String reply) {
    }
}
