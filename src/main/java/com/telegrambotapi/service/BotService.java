package com.telegrambotapi.service;


import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.telegrambotapi.TelegramBotApiApplication;
import com.telegrambotapi.config.BotConfig;
import com.telegrambotapi.constant.BotConstants;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class BotService extends TelegramLongPollingBot {

   @Autowired
   public static UserService userService;

   @Autowired
   public static Storage storage;

   public final BotConfig config;

   public static ChatGPTService chatGPTService;

   public static Environment environment;

   private String currentMode = "/default";

   private String expectedName = null;

   private String expectedBirthday = null;

   public BotService(BotConfig config,
                     Storage storage,
                     UserService userService) throws TelegramApiException {
      this.config = config;
      this.userService = userService;
      this.storage = storage;

      CompletableFuture.runAsync(() -> {
         try {
            initialMenu();
         } catch (TelegramApiException e) {
            throw new RuntimeException(e);
         }
      });
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

   // ====  MAIN START  ================================================================================================
   @Override
   public void onUpdateReceived(Update update) {
      Firestore dbFirestore = FirestoreClient.getFirestore();


      try {
         // ====  SEND MESSAGE  ========================================================================================
         if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            // ====  1ST ENTRY - INITIALIZATION USER
            while (!userService.userIdExists(dbFirestore, String.valueOf(chatId))) {
               userService.createUser(update.getMessage());
            }

            String username = userService.getUsername(update.getMessage());
            String userBirthday = userService.getUserBirthday(update.getMessage());

            if (messageText.equals("/start") && username == null) {
               sendMessage(chatId, BotConstants.FIRST_GREETING);
               expectedName = update.getMessage().getChat().getUserName();

            } else if (messageText != null && expectedName != null) {
               username = messageText;
               String name = messageText.trim();
               userService.updateUserName(chatId, username);

               sendMessage(chatId, BotConstants.BIRTHDAY_FORMAT);

               expectedBirthday = name;
               expectedName = null;

            } else if (expectedBirthday != null && userBirthday == null) {
               String birthday = messageText.trim();

               if (messageText.matches(BotConstants.BIRTHDAY_REGEX)) {
                  userService.updateBirthday(update, birthday);
                  expectedBirthday = null;

                  sendMessage(chatId, BotConstants.BIRTHDAY_EXCEPT);
                  startCommandReceived(chatId, username);
                  assistantActivate(chatId);

               } else {
                  sendMessage(chatId, BotConstants.BIRTHDAY_ERROR);
               }
               // ==== CASES  ==========================================================================================
            } else {
               switch (messageText) {
                  case "/start" -> {
                     currentMode = "/start";
                     sendMessage(chatId, "Здравствуйте, " + username);
                     assistantActivate(chatId);
                  }
                  case "/help" -> {
                     currentMode = "/help";
                     System.out.println("Команда /help выполняется в потоке: " + Thread.currentThread().getName());
                     sendMessage(chatId, BotConstants.HELP_TEXT);
                  }
                  case "/assistant" -> {
                     currentMode = "/assistant";
                     assistantActivate(chatId);
                  }
                  case "/photo" -> {
                     currentMode = "/photo";
                     sendMessage(chatId, "Добавь своё фото. Нажми на \uD83D\uDCCE");
                     sendImage(chatId);
                  }
                  default -> {
                     if ("/assistant".equals(currentMode)) {
                        sendMessage(chatId, askGpt(messageText));
                     }
                  }
               }
            }
            // ====  CALLBACK QUERY  ===================================================================================
         } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long callbackMessageId = update.getCallbackQuery().getMessage().getMessageId();
            long callbackChatId = update.getCallbackQuery().getMessage().getChatId();

            switch (callbackData) {
               case BotConstants.YES_BUTTON -> {
                  currentMode = "/assistant";
                  String text = askGpt(BotConstants.HELLO_ASSISTANT);
                  executeEditMessageText(text, callbackChatId, callbackMessageId);
               }
               case BotConstants.NO_BUTTON -> {
                  currentMode = "/default";
                  executeEditMessageText(BotConstants.REJECTION_ASSISTANT, callbackChatId, callbackMessageId);
               }
            }
            // ==== ADD PHOTO  =========================================================================================
         } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
            Long chatId = update.getMessage().getChatId();

            // GET PHOTO FROM MESSAGE
            List<PhotoSize> photos = update.getMessage().getPhoto();
            PhotoSize photo = photos.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);

            sendMessage(chatId,
                    "Спасибо за отправку фото!\nВы получите уведомление, после того, как результат будет готов");

            // ASYNC GET VALIDATION RESULT
            CompletableFuture<Double> future = CompletableFuture.supplyAsync(() -> {
               try {
                  return photoValidation(photo).get();
               } catch (InterruptedException | ExecutionException e) {
                  throw new RuntimeException(e);
               }
            });

            // ASYNC WAITING FOR AN ANSWER
            future.thenApply(chance -> {
               try {
                  int roundedChance = (int) Math.round(chance * 100);
                  String fileId = photo.getFileId();
                  GetFile getFile = new GetFile();
                  getFile.setFileId(fileId);

                  execute(getFile);

                  sendMessage(chatId, "Мы проверили твоё фото!\nМожешь ознакомится с результатом");
                  sendMessage(chatId, String.format(
                          "Вероятность прожить дольше, если прямо сейчас покаяться и взять себя в руки\n%d%%",
                          roundedChance));

                  return null;
               } catch (TelegramApiException e) {
                  throw new RuntimeException(e);
               }
            });
         }
      } catch (ExecutionException | InterruptedException | TelegramApiException ignored) {
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }
   // =====  MAIN END  =================================================================================================

   public void sendImage(Long chatId) throws IOException {
      String bucketName = "umatch-ef0db.appspot.com";
      String blobName = "photos/jackie-chan.jpg";

      String pathToServiceAccountKey = "path/to/serviceAccountKey.json";

      // ==== TELEGRAM BOT LOADER
      ClassLoader classLoader = BotService.class.getClassLoader();

      File fileBucket = new File(Objects.requireNonNull(classLoader.getResource(pathToServiceAccountKey)).getFile());
      FileInputStream serviceAccountBucket =
              new FileInputStream(fileBucket.getAbsoluteFile());

      // Создаем объект Storage с указанием ID проекта
//      Storage storage = StorageOptions.newBuilder()
//              .setProjectId("umatch-ef0db")
//              .setCredentials(GoogleCredentials.fromStream(serviceAccountBucket))
//              .build().getService();

      // Получение URL-адреса изображения
      Blob blob = storage.get(bucketName, blobName);

      storage.list().iterateAll().forEach(bucketExpected -> System.out.println(bucketExpected.getName()));


      String url = blob.getMediaLink();

//      String url = signedUrl.toString();

      // Загрузка изображения
      try (InputStream inputStream = new URL(url).openStream()) {
         InputFile inputFile = new InputFile(inputStream, "jackie-chan.jpg");
         SendPhoto sendPhoto = new SendPhoto();
         sendPhoto.setChatId(chatId);
         sendPhoto.setPhoto(inputFile);

         execute(sendPhoto);
      } catch (TelegramApiException e) {
         throw new RuntimeException(e);
      }
   }

   // GET ANSWER FROM ChatGPT
   public String askGpt(String text) {
      System.out.println("Метод askGpt выполняется в потоке: " + Thread.currentThread().getName());
      log.info("Метод askGpt выполняется в потоке: {}", Thread.currentThread().getName());
      return chatGPTService.askChatGPTText(text);
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

   // EXECUTE EDIT MESSAGE TEXT
   private void executeEditMessageText(String text, long chatId, long messageId) throws TelegramApiException {
      EditMessageText message = new EditMessageText();
      message.setChatId(String.valueOf(chatId));
      message.setText(text);
      message.setMessageId((int) messageId);

      execute(message);
   }

   // START BUTTONS ASSISTANT
//   @Async
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

   // INITIAL MENU
   @Async
   public CompletableFuture<Void> initialMenu() throws TelegramApiException {
      System.out.println("Метод initialMenu выполняется в потоке: " + Thread.currentThread().getName());
      List<BotCommand> listOfCommands = new ArrayList<>();
      listOfCommands.add(new BotCommand("/start", "Начать пользование"));
      listOfCommands.add(new BotCommand("/assistant", "Перейти к личному ассистенту"));
      listOfCommands.add(new BotCommand("/photo", "Перейти к добавлению фото"));
      listOfCommands.add(new BotCommand("/help", "Запросить помощь по командам"));

      this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));

      log.info("Метод initialMenu выполняется в потоке: {}", Thread.currentThread().getName());
      return CompletableFuture.completedFuture(null);
   }

   // ASYNC PHOTO VALIDATION
   @Async
   public CompletableFuture<Double> photoValidation(PhotoSize photoSize) throws InterruptedException {
      Random random = new Random();
      double chance = 0.1 + random.nextDouble() * 0.57;

      for (int i = 10; i >= 0; i--) {
         Thread.sleep(1_000);
         System.out.println(Thread.currentThread().getName() + " := waiting...  " + i);
      }

      return CompletableFuture.completedFuture(chance);
   }

   // ASYNC TEST MESSAGE
   @Async
   public CompletableFuture<Void> sendAsyncMessage(Long chatId, String text) {
      System.out.println("Метод sendAsyncMessage выполняется в потоке: " + Thread.currentThread().getName());
      SendMessage message = new SendMessage();
      message.setChatId(String.valueOf(chatId));
      message.setText(text);

      try {
         for (int i = 10; i >= 0; i--) {
            Thread.sleep(1_000);
            System.out.println(Thread.currentThread().getName() + " := waiting...  " + i);
         }

         log.info("Метод sendAsyncMessage выполняется в потоке: {}", Thread.currentThread().getName());
         execute(message);

         return CompletableFuture.completedFuture(null);

      } catch (TelegramApiException e) {
         CompletableFuture<Void> future = new CompletableFuture<>();
         future.completeExceptionally(e); // Завершить CompletableFuture с исключением

         return future;

      } catch (InterruptedException e) {
         throw new RuntimeException(e);
      }
   }
}
