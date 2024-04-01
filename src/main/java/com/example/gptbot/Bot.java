package com.example.gptbot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Bot extends TelegramLongPollingBot {
    private static String context = "";
    private final String voiceEndpoint = "https://api.voice.steos.io/v1/get/tts";
    private final String voiceApiKey = "73724f57-2075-4698-975a-de5f3873ab8a";
    private final String botToken = "6699199033:AAFvbdveVGoGkpz_Otpqqgh-LreAiAByt1w";
    private final String gptApiKey = "sk-cff02918c42843c49a2298b85c48ea82";
    private final String gptEndpoint = "https://api.deepseek.com/v1/chat/completions";
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();

            String userMessage = message.getText().replace("\"", "").replace("\n","");
            saveRequestToFile(userMessage);

            String gptResponse = null;
            try {
                gptResponse = getGPTResponse(userMessage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            context += extractContentFromResponse(gptResponse) + " ";
            System.out.println(gptResponse);
            SendAudio sendAudio = new SendAudio();
            sendAudio.setChatId(chatId);
            sendAudio.setAudio(sendAudioToTelegram(requestToAudioUrl(extractContentFromResponse(gptResponse))));

            try {
                execute(sendAudio);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }

        }
    }
        private String getGPTResponse (String userMessage) throws IOException, InterruptedException {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                     .uri(URI.create(gptEndpoint))
                     .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + gptApiKey)
                     .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(userMessage, context)))
                     .build();
             HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.body());
             return response.body();
        }
    private void updateContext(String apiResponse) {
        String contextKey = "\"context\":\"";
        int contextStartIndex = apiResponse.indexOf(contextKey);
        if (contextStartIndex != -1) {
            int contextEndIndex = apiResponse.indexOf("\"", contextStartIndex + contextKey.length());
            if (contextEndIndex != -1) {
                context = apiResponse.substring(contextStartIndex + contextKey.length(), contextEndIndex);
            }
        }
    }
    private void saveRequestToFile(String requestBody) {
        try {
            // Замените "requests.txt" на путь к вашему файлу
            Files.write(Path.of("requests.txt"), (requestBody + "\n").getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private String extractContentFromResponse(String apiResponse) {
        try {
            JSONObject jsonResponse = new JSONObject(apiResponse);
            JSONArray choicesArray = jsonResponse.getJSONArray("choices");

            // Предполагаем, что мы хотим получить первый элемент массива

            JSONObject firstChoice = choicesArray.getJSONObject(0);
            JSONObject messageObject = firstChoice.getJSONObject("message");
            // Извлекаем значения полей "field1" и "field2"
            String field1Value = messageObject.getString("content");

            // Комбинируем два поля в одну строку
            return field1Value.replace("\"", "").replace("\n","");
        } catch (Exception e) {
            e.printStackTrace();
            return "Error extracting content from response";
        }
    }
    private String requestToAudioUrl(String message) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(voiceEndpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", voiceApiKey)
                .POST(HttpRequest.BodyPublishers.ofString("{\"voice_id\": 343, \"text\": \"" + message + "\", \"format\": \"mp3\"}"))
                .build();
        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println(response);

        JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();

        String audioUrl = jsonObject.get("audio_url").getAsString();

        return audioUrl;
    }
    public InputFile sendAudioToTelegram(String AUDIO_URL) {
        // Скачиваем аудиофайл
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(AUDIO_URL))
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

            // Сохраняем аудиофайл локально
            Path audioFilePath = Path.of("downloaded_audio.mp3");
            Files.write(audioFilePath, response.body());

            return new InputFile(audioFilePath.toFile());


        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return new InputFile();
    }
    private String buildRequestBody(String userMessage, String context) {
        if (context.equals("")) {
            return "{\n" +
                    "  \"messages\": [\n" +
                    "    {\n" +
                    "      \"content\": \"" + userMessage + "\",\n" +
                    "      \"role\": \"user\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"model\": \"deepseek-chat\"\n" +
                    "}";
        } else {
            return "{\n" +
                "  \"messages\": [\n" +
                        "    {\n" +
                        "      \"content\": \"" + context + "\",\n" +
                        "      \"role\": \"assistant\"\n" +
                        "    },\n" +
                        "    {\n" +
                        "      \"content\": \"" + userMessage + "\",\n" +
                        "      \"role\": \"user\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"model\": \"deepseek-chat\"\n" +
                        "}";
        }
    }
    @Override
    public String getBotUsername() {
        return "UselessAiHelperBot";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}
