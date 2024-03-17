package com.telegrambotapi.service;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.telegrambotapi.config.BotConfig;
import com.telegrambotapi.constants.BotConstants;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
@Slf4j
public class BotService extends TelegramLongPollingBot {

   public final BotConfig config;
   public static ChatGPTService chatGPTService;

   @Autowired
   public static UserService userService;

   public static Environment environment;
   private String currentMode = "default";
   private String expectedName = null;
   private String expectedBirthday = null;

   public BotService(BotConfig config, UserService userService) throws TelegramApiException {
      this.config = config;
      this.userService = userService;

      List<BotCommand> listOfCommands = new ArrayList<>();
      listOfCommands.add(new BotCommand("/start", "Начать пользование"));
      listOfCommands.add(new BotCommand("/assistant", "Перейти к личному ассистенту"));
      listOfCommands.add(new BotCommand("/help", "Запросить помощь по командам"));

      this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
   }

   @Override
   public String getBotUsername() {
      return config.getBotName();
   }

   @Override
   public String getBotToken() {
      return config.getToken();
   }

   @Autowired
   public void setService(ChatGPTService service) {
      chatGPTService = service;
   }

   @Autowired
   public void setEnv(Environment environment) {
      BotService.environment = environment;
   }


   @Override
   public void onUpdateReceived(Update update) {
      SendMessage sendMessage = new SendMessage();
      Firestore dbFirestore = FirestoreClient.getFirestore();

      try {
         if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            // =====   1ST ENTRY - INITIALIZATION USER   ==================================================================
               if (!userService.userIdExists(dbFirestore, String.valueOf(chatId))) {
                  userService.createUser(update.getMessage());
                  Thread.sleep(3_000);
               }

               String username = userService.getUsername(update.getMessage());
               String userBirthday = userService.getUserBirthday(update.getMessage());

               if (messageText.equals("/start") && username == null) {
                  sendMessage(chatId, "Как я могу к вам обращаться:");
                  expectedName = update.getMessage().getChat().getUserName(); // Устанавливаем ожидание имени для этого чата

               } else if (messageText != null && expectedName != null) {
                  username = messageText;
                  String name = messageText.trim();
                  userService.updateUserName(chatId, username);

                  sendMessage(chatId, "Ваша дата рождения:\n" +
                          "DD/MM/YYYY");

                  expectedBirthday = name;
                  expectedName = null;

               } else if (expectedBirthday != null && userBirthday == null) {
                  String birthday = messageText.trim();

                  if (messageText.matches("^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/\\d{4}$")) {
                     userService.updateBirthday(update, birthday);

                     expectedBirthday = null;
                     sendMessage(chatId, "Спасибо! Ваше имя и день рождения записаны.");
                     startCommandReceived(chatId, username);

                     assistantActivate(chatId);

                  } else {
                     sendMessage(chatId,
                             "Извините, команда не была распознана. Пожалуйста, введите дату рождения в формате\n" +
                             "DD/MM/YYYY");
                  }
                  // =====   CASES   ===================================================================================
               } else {
                  switch (messageText) {
                     case "/start" -> {
                        sendMessage(chatId, "Здравствуйте, " + username);
                        assistantActivate(chatId);
                     }

                     case "/help" -> {
                        prepareAndSendMessage(chatId, BotConstants.HELP_TEXT);
                        currentMode = "/help";
                     }

                     case "/assistant" -> {
                        assistantActivate(chatId);
                        currentMode = "/assistant";
                     }

                     default -> {
                        if ("/assistant".equals(currentMode)) {
                           askGpt(sendMessage, String.valueOf(chatId), messageText);
                        }
                     }
                  }
               }
            // =====   CALLBACK QUERY   ================================================================================
         } else if (update.hasCallbackQuery()) {

               String callbackData = update.getCallbackQuery().getData();
               long callbackMessageId = update.getCallbackQuery().getMessage().getMessageId();
               long callbackChatId = update.getCallbackQuery().getMessage().getChatId();

               switch (callbackData) {
                  case BotConstants.YES_BUTTON -> {
                     String text = BotConstants.HELLO_ASSISTANT;
                     EditMessageText editMessageText = new EditMessageText();
                     editMessageText.setChatId(callbackChatId);
                     editMessageText.setText(text);
                     editMessageText.setMessageId(Integer.parseInt(String.valueOf(callbackMessageId)));
                     currentMode = "/assistant";

                     execute(editMessageText);
                     askGpt(BotConstants.HELLO_ASSISTANT);
                  }

                  case BotConstants.NO_BUTTON -> {
                     String text = "Если захотите задать вопрос личному ассистенту вы можете перейти в меню\n" +
                             "Или просто нажать здесь /assistant";
                     EditMessageText editMessageText = new EditMessageText();
                     editMessageText.setChatId(callbackChatId);
                     editMessageText.setText(text);
                     editMessageText.setMessageId(Integer.parseInt(String.valueOf(callbackMessageId)));
                     currentMode = "/start";

                     execute(editMessageText);
                  }
               }
         }
      } catch (ExecutionException | InterruptedException | TelegramApiException ignored) {
      }
   }

   private String askGpt(String text) {
      return chatGPTService.askChatGPTText(text);
   }

   /**
    * Sends your request to chatGPT using #ChatGPTService.askChatGPT
    *
    * @param sendMessage
    * @param chatId
    * @param text
    */
   private String askGpt(SendMessage sendMessage, String chatId, String text) {
      String gptResponse = chatGPTService.askChatGPTText(text);

      try {
         sendMessage.setChatId(chatId);
         sendMessage.setText(gptResponse);
         execute(sendMessage);

      } catch (TelegramApiException e) {
         throw new RuntimeException(e);
      }

      return gptResponse;
   }

   /**
    * Gives a feedback about what you are requesting to chatGPT
    *
    * @param sendMessage
    * @param chatId
    * @param text
    */
   private void pong(SendMessage sendMessage, String chatId, String text) {
      sendMessage.setText("Отправил сообщение к chatGPT: " + text);

      try {
         sendMessage.setChatId(chatId);
         execute(sendMessage);

      } catch (TelegramApiException e) {
         e.printStackTrace();
      }
   }

   // START
   private void startCommandReceived(Long chatId, String name) throws TelegramApiException {
      String answer = EmojiParser.parseToUnicode(String.format("Здравствуй, %s, Приятно познакомится!, \uD83D\uDE0A", name));
      sendMessage(chatId, answer);
   }

   // SEND MESSAGE
   private void sendMessage(Long chatId, String textToSend) throws TelegramApiException {
      SendMessage message = new SendMessage();
      message.setChatId(String.valueOf(chatId));
      message.setText(textToSend);
      execute(message);
   }

   // EXECUTE MESSAGE
   private void executeMessage(SendMessage message) throws TelegramApiException {
      execute(message);
   }

   // PREPARE AND SEND MESSAGE
   private void prepareAndSendMessage(long chatId, String textToSend) throws TelegramApiException {
      SendMessage message = new SendMessage();
      message.setChatId(String.valueOf(chatId));
      message.setText(textToSend);

      executeMessage(message);
   }

   private void executeEditMessageText(String text, long chatId, long messageId) throws TelegramApiException {
      EditMessageText message = new EditMessageText();
      message.setChatId(String.valueOf(chatId));
      message.setText(text);
      message.setMessageId((int) messageId);

      execute(message);
   }

   public void assistantActivate(Long chatId) throws TelegramApiException {
      SendMessage message = new SendMessage();
      message.setChatId(String.valueOf(chatId));
      message.setText("Чем я могу вам помочь?\nХотите пообщаться с ChatGPT?");

      InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
      List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
      List<InlineKeyboardButton> rowInline = new ArrayList<>();

      InlineKeyboardButton yesButton = new InlineKeyboardButton();
      yesButton.setText("Да!");
      yesButton.setCallbackData(BotConstants.YES_BUTTON);

      InlineKeyboardButton noButton = new InlineKeyboardButton();
      noButton.setText("Нет!");
      noButton.setCallbackData(BotConstants.NO_BUTTON);

      rowInline.add(yesButton);
      rowInline.add(noButton);
      rowsInline.add(rowInline);
      markupInline.setKeyboard(rowsInline);
      message.setReplyMarkup(markupInline);

      execute(message);
   }
}
