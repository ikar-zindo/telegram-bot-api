package com.telegrambotapi.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;
import com.telegrambotapi.config.BotConfig;
import com.telegrambotapi.domain.User;
import com.telegrambotapi.exception.UserException;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Component
@Slf4j
public class BotService extends TelegramLongPollingBot {

   public final BotConfig config;

   static final String YES_BUTTON = "YES_BUTTON";
   static final String NO_BUTTON = "NO_BUTTON";
   static final String ERROR_TEXT = "Error occurred: ";

   static final String HELP_TEXT = "This bot is created to demonstrate Spring capabilities.\n\n" +
           "You can execute commands from the main menu on the left or by typing a command:\n\n" +
           "Time /start to see a welcome message\n\n" +
           "Type /birthday to see data stored about yourself\n\n" +
           "Type /deleteBirthday delete your birthday data from DB\n\n" +
           "Type /help to see this message again";

   public BotService(BotConfig config) {
      this.config = config;
      List<BotCommand> listOfCommands = new ArrayList<>();
      listOfCommands.add(new BotCommand("/start", "get a welcome message"));
      listOfCommands.add(new BotCommand("/help", "help how to use bot"));

      try {
         this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
      } catch (TelegramApiException e) {
         log.error("Error setting bot`s command list: " + e.getMessage());
      }
   }

   @Override
   public String getBotUsername() {
      return config.getBotName();
   }

   @Override
   public String getBotToken() {
      return config.getToken();
   }

   private String expectedName = null;
   private String expectedBirthday = null;

   @Override
   public void onUpdateReceived(Update update) {
      if (update.hasMessage() && update.getMessage().hasText()) {
         Long chatId = update.getMessage().getChatId();
         String messageText = update.getMessage().getText();

         // =====   1ST ENTRY - INITIALIZATION USER   ==================================================================
         try {
            createUser(update.getMessage());
            String username = getUsername(update.getMessage());
            String userBirthday = getUserBirthday(update.getMessage());

            if (messageText.equals("/start") && username == null) {
               sendMessage(chatId, "Как я могу к вам обращаться:");
               expectedName = update.getMessage().getChat().getUserName(); // Устанавливаем ожидание имени для этого чата

            } else if (messageText != null && expectedName != null) {
               username = messageText;
               String name = messageText.trim();
               updateUserName(chatId, username);

               sendMessage(chatId, "Ваша дата рождения:\n" +
                       "DD/MM/YYYY");

               expectedBirthday = name;
               expectedName = null;

            } else if (expectedBirthday != null && userBirthday == null) {
               String birthday = messageText.trim();

               if (messageText.matches("^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/\\d{4}$")) {
                  updateBirthday(update, birthday);

                  expectedBirthday = null;
                  sendMessage(chatId, "Спасибо! Ваше имя и день рождения записаны.");
                  startCommandReceived(chatId, username);

               } else {
                  sendMessage(chatId, "Извините, команда не была распознана. Пожалуйста, введите дату рождения в формате\n" +
                          "YYYY-MM-DD.");
               }
               // =====   CASES   ======================================================================================
            } else {
               switch (messageText) {
                  case "/start" -> {
                     startCommandReceived(chatId, username);
                     register(chatId);
                  }

                  case "/help" -> prepareAndSendMessage(chatId, HELP_TEXT);

                  default -> sendMessage(chatId, "Извините, команда не была распознана.");
               }
            }
         } catch (ExecutionException | InterruptedException ignored) {
         }
      }
   }

   // START
   private void startCommandReceived(Long chatId, String name) {
      String answer = EmojiParser.parseToUnicode(String.format("Здравствуй, %s, Приятно познакомится!, \uD83D\uDE0A", name));

      sendMessage(chatId, answer);
      log.info("Replied to user " + name);
   }

   // SEND MESSAGE
   private void sendMessage(Long chatId, String textToSend) {
      SendMessage message = new SendMessage();
      message.setChatId(String.valueOf(chatId));
      message.setText(textToSend);

      try {
         execute(message);
      } catch (TelegramApiException e) {
         log.error("Error occurred: " + e.getMessage());
      }
   }

   // CREATE - USER
   public void createUser(Message message)
           throws ExecutionException, InterruptedException {

      Firestore dbFirestore = FirestoreClient.getFirestore();
      String chatId = String.valueOf(message.getChatId());

      if (!userIdExists(dbFirestore, chatId)) {
         User user = new User();

         user.setUserId(Long.valueOf(chatId));
         user.setCreatedAt(new Date());

         ApiFuture<WriteResult> future =
                 dbFirestore.collection("users").document(chatId).set(user);

         log.info("User saved in DB: " + user);
      }
   }

   // GET - USERNAME
   public String getUsername(Message message) throws ExecutionException, InterruptedException {
      if (message != null) {
         Firestore dbFirestore = FirestoreClient.getFirestore();
         DocumentReference documentReference =
                 dbFirestore.collection("users").document(String.valueOf(message.getChatId()));

         ApiFuture<DocumentSnapshot> future = documentReference.get();
         DocumentSnapshot documentSnapshot = future.get();

         User user;

         if (documentSnapshot.exists()) {
            user = documentSnapshot.toObject(User.class);
            String username = user.getName();

            return username;

         } else {
            throw new UserException("User not found!");
         }
      } else {
         throw new UserException("Message is empty!");
      }
   }

   // GET - USER BIRTHDAY
   public String getUserBirthday(Message message) throws ExecutionException, InterruptedException {
      if (message != null) {
         Firestore dbFirestore = FirestoreClient.getFirestore();
         DocumentReference documentReference =
                 dbFirestore.collection("users").document(String.valueOf(message.getChatId()));

         return Objects.requireNonNull(documentReference.get().get().toObject(User.class)).getUserBirthday();

      } else {
         throw new UserException("");
      }
   }

   // UPDATE - CHANGE NAME
   public void updateUserName(Long chatId, String name) throws ExecutionException, InterruptedException {
      if (name != null) {
         Firestore dbFirestore = FirestoreClient.getFirestore();
         DocumentReference documentReference = dbFirestore.collection("users").document(String.valueOf(chatId));

         ApiFuture<DocumentSnapshot> future = documentReference.get();
         DocumentSnapshot documentSnapshot = future.get();

         User user;

         if (documentSnapshot.exists()) {
            user = documentSnapshot.toObject(User.class);

            user.setName(name);
            dbFirestore.collection("users").document(String.valueOf(user.getUserId())).set(user);
         }
      } else {
         throw new UserException("Name is empty");
      }
   }

   // EXECUTE MESSAGE
   private void executeMessage(SendMessage message) {
      try {
         execute(message);
      } catch (TelegramApiException e) {
         log.error(ERROR_TEXT + e.getMessage());
      }
   }

   // PREPARE AND SEND MESSAGE
   private void prepareAndSendMessage(long chatId, String textToSend) {
      SendMessage message = new SendMessage();
      message.setChatId(String.valueOf(chatId));
      message.setText(textToSend);
      executeMessage(message);
   }

   // UPDATE - USER BIRTHDAY
   public void updateBirthday(Update update, String date) throws ExecutionException, InterruptedException {
      String userId = update.getMessage().getChatId().toString();

      Firestore dbFirestore = FirestoreClient.getFirestore();
      DocumentReference documentReference = dbFirestore.collection("users").document(userId);

      ApiFuture<DocumentSnapshot> future = documentReference.get();
      DocumentSnapshot documentSnapshot = future.get();

      if (documentSnapshot.exists()) {
         User user = documentSnapshot.toObject(User.class);

         user.setUserBirthday(date);
         dbFirestore.collection("users").document(String.valueOf(user.getUserId())).set(user);

         log.info(
                 String.format("User(userId=%s): changed date of birth to {%s}",
                         user.getUserId(), date));
      }
   }

   // CHECK ID IN DB
   private boolean userIdExists(Firestore dbFirestore, String userId)
           throws ExecutionException, InterruptedException {

      return dbFirestore.collection("users").document(userId).get().get().exists();
   }

   // ============   EXPERIMENTS   ================================

   // REGISTRATION QUESTION
   private void register(Long chatId) {

      SendMessage message = new SendMessage();
      message.setChatId(String.valueOf(chatId));
      message.setText("Хотите пообщаться с ChatGPT");

      InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
      List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
      List<InlineKeyboardButton> rowInline = new ArrayList<>();

      var yesButton = new InlineKeyboardButton();
      yesButton.setText("Yes");
      yesButton.setCallbackData(YES_BUTTON);

      var noButton = new InlineKeyboardButton();
      noButton.setText("No");
      noButton.setCallbackData(NO_BUTTON);

      rowInline.add(yesButton);
      rowInline.add(noButton);
      rowsInline.add(rowInline);
      markupInline.setKeyboard(rowsInline);
      message.setReplyMarkup(markupInline);

      try {
         execute(message);
      } catch (TelegramApiException e) {
         log.error("Error occurred: " + e.getMessage());
      }
   }

   // READ - USER BIRTHDAY
   public String checkBirthday(Long chatId) throws UserException, ExecutionException, InterruptedException {
      if (chatId != null) {
         Firestore dbFirestore = FirestoreClient.getFirestore();
         DocumentReference documentReference = dbFirestore.collection("users").document(String.valueOf(chatId));

         ApiFuture<DocumentSnapshot> future = documentReference.get();
         DocumentSnapshot documentSnapshot = future.get();

         if (documentSnapshot.exists()) {
            User user = documentSnapshot.toObject(User.class);
            String date = user.getUserBirthday();

            return date;
         } else {
            throw new UserException(String.format(
                    "User not found in DB with id=%s", String.valueOf(chatId)));
         }
      } else {
         throw new UserException("ChatId not found!");
      }
   }

   private void executeEditMessageText(String text, long chatId, long messageId) {
      EditMessageText message = new EditMessageText();
      message.setChatId(String.valueOf(chatId));
      message.setText(text);
      message.setMessageId((int) messageId);

      try {
         execute(message);
      } catch (TelegramApiException e) {
         log.error(ERROR_TEXT + e.getMessage());
      }
   }
}
