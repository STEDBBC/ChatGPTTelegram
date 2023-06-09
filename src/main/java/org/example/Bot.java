package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import okhttp3.*;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bot extends TelegramLongPollingBot {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);

    private static final String TELEGRAM_BOT_USERNAME = "honey_name_bot";
    private static String TELEGRAM_BOT_TOKEN;
    private static String OPENAI_API_KEY;

    private static final String GPT_API_URL = "https://api.openai.com/v1/engines/davinci-codex/completions";

    private final OkHttpClient client;
    private final Gson gson;

    public Bot() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Please provide Telegram bot token and OpenAI API key as command line arguments.");
            System.exit(1);
        }

        TELEGRAM_BOT_TOKEN = args[0];
        OPENAI_API_KEY = args[1];

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new Bot());
        } catch (Exception e) {
            LOGGER.info("Failed to register bot: " + e.getMessage());
        }
    }


    @Override
    public String getBotUsername() {
        return TELEGRAM_BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return TELEGRAM_BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String chatGPTResponse = getChatGPTResponse(update.getMessage().getText());
            handleSendMessage(update.getMessage().getChatId(), chatGPTResponse);
        }
    }


    private SendMessage createSendMessage(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .build();
    }

    private void handleSendMessageError(Exception e) {
        LOGGER.warn("Failed to send message: " + e.getMessage());
    }
    private String removeQuestionFromResponse(String question, String response) {
        return response.replace(question, "").trim();
    }

    private String getChatGPTResponse(String userInput) {
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("prompt", "Модель GPT-4, пожалуйста, ответь на вопрос на русском языке: " + userInput + "\n\n");
        requestBodyMap.put("max_tokens", 1600);
        requestBodyMap.put("n", 1);
        requestBodyMap.put("stop", null);
        requestBodyMap.put("temperature", 0.5);
        String requestBody = gson.toJson(requestBodyMap);

        Request request = new Request.Builder()
                .url(GPT_API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .post(RequestBody.create(requestBody, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            Map<String, Object> responseMap = gson.fromJson(responseBody, Map.class); // Измените тип переменной на Map
            String chatGPTResponse = ((Map<String, String>) ((ArrayList) responseMap.get("choices")).get(0)).get("text");
            chatGPTResponse = removeQuestionFromResponse(userInput, chatGPTResponse);
            return chatGPTResponse;

        } catch (IOException e) {
            LOGGER.warn("Failed to get ChatGPT response: " + e.getMessage());
            return "Error: Could not connect to ChatGPT API.";
        }
    }
    private void handleSendMessage(Long chatId, String text) {
        if (text == null || text.trim().isEmpty()) {
            LOGGER.warn("Received empty message from ChatGPT API. Skipping message sending.");
            return;
        }

        SendMessage message = createSendMessage(chatId, text);
        try {
            execute(message);
        } catch (Exception e) {
            handleSendMessageError(e);
        }
    }

}