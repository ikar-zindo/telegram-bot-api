package com.telegrambotapi.service;

import com.telegrambotapi.constant.BotConstants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatGPTService {

   public static final String CHOICES = "choices";

   @Value("${bot.text.temperature}")
   Double temperature;

   @Value("${bot.text.model}")
   String textModel;

   @Value("${bot.text.top-p}")
   Double topP;

   @Value("${bot.text.frequency-penalty}")
   Double freqPenalty;

   @Value("${bot.text.presence-penalty}")
   Double presPenalty;

   @Value("${api.token}")
   String apiToken;

   @Value("${bot.text.max-tokens}")
   Integer maxTokens;

   @Value("${api.url.completions}")
   String urlCompletions;

   public String askChatGPTText(String msg) {
      RestTemplate restTemplate = new RestTemplate();
      HttpHeaders headers = setHeaders();

      Map<String, Object> request = new HashMap<>();
      request.put(BotConstants.MODEL, textModel);
      request.put(BotConstants.TEMPERATURE, temperature);
      request.put(BotConstants.MAX_TOKENS, maxTokens);
      request.put(BotConstants.TOP_P, topP);
      request.put(BotConstants.FREQUENCY_PENALTY, freqPenalty);
      request.put(BotConstants.PRESENCE_PENALTY, presPenalty);

      List<Map<String, Object>> messages = new ArrayList<>();
      Map<String, Object> message = new HashMap<>();
      message.put("role", "user");
      message.put("content", msg);
      messages.add(message);
      request.put("messages", messages);

      URI chatGptUrl = URI.create(urlCompletions);
      ResponseEntity<String> responseEntity = restTemplate
              .postForEntity(chatGptUrl, new HttpEntity<>(request, headers), String.class);

      JSONObject responseJson = new JSONObject(responseEntity.getBody());
      JSONArray choices = (JSONArray) responseJson.get(CHOICES);

      JSONObject firstChoice = choices.getJSONObject(0);
      JSONObject messageObject = firstChoice.getJSONObject("message");

      return messageObject.getString("content");
   }

   private HttpHeaders setHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.add(BotConstants.AUTHORIZATION, apiToken);
      return headers;
   }
}
