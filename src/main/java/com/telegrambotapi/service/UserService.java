package com.telegrambotapi.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.telegrambotapi.model.User;
import com.telegrambotapi.exception.UserException;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutionException;


@Service
public class UserService {
   public Firestore dbFirestore = FirestoreClient.getFirestore();

   // CREATE - USER
   public void createUser(Message message)
           throws ExecutionException, InterruptedException {

      String chatId = String.valueOf(message.getChatId());

      if (!userIdExists(dbFirestore, chatId)) {
         User user = new User();

         user.setUserId(Long.valueOf(chatId));
         user.setCreatedAt(new Date());

         dbFirestore.collection("users").document(chatId).set(user);
      }
   }

   // GET - USERNAME
   public String getUsername(Message message) throws ExecutionException, InterruptedException {
      if (message != null) {
         DocumentReference documentReference =
                 dbFirestore.collection("users").document(String.valueOf(message.getChatId()));

         ApiFuture<DocumentSnapshot> future = documentReference.get();
         DocumentSnapshot documentSnapshot = future.get();

         User user;

         if (documentSnapshot.exists()) {
            user = documentSnapshot.toObject(User.class);

            assert user != null;
            return user.getName();

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
         DocumentReference documentReference =
                 dbFirestore.collection("users").document(String.valueOf(message.getChatId()));

         return Objects.requireNonNull(documentReference.get().get().toObject(User.class)).getUserBirthday();

      } else {
         throw new UserException("Message is null!");
      }
   }

   // UPDATE - USERNAME
   public void updateUserName(Long chatId, String name) throws ExecutionException, InterruptedException {
      if (name != null) {
         DocumentReference documentReference = dbFirestore.collection("users").document(String.valueOf(chatId));

         ApiFuture<DocumentSnapshot> future = documentReference.get();
         DocumentSnapshot documentSnapshot = future.get();

         User user;

         if (documentSnapshot.exists()) {
            user = documentSnapshot.toObject(User.class);

            if (user != null) {
               user.setName(name);
               dbFirestore.collection("users").document(String.valueOf(user.getUserId())).set(user);

            } else {
               throw new UserException("");
            }
         }
      } else {
         throw new UserException("Name is empty");
      }
   }

   // UPDATE - USER BIRTHDAY
   public void updateBirthday(Update update, String date) throws ExecutionException, InterruptedException {
      String userId = update.getMessage().getChatId().toString();
      DocumentReference documentReference = dbFirestore.collection("users").document(userId);

      ApiFuture<DocumentSnapshot> future = documentReference.get();
      DocumentSnapshot documentSnapshot = future.get();

      if (documentSnapshot.exists()) {
         User user = documentSnapshot.toObject(User.class);

         if (user != null) {
            user.setUserBirthday(date);
            dbFirestore.collection("users").document(String.valueOf(user.getUserId())).set(user);
         }
      }
   }

   // CHECK USER ID IN DB
   public boolean userIdExists(Firestore dbFirestore, String userId)
           throws ExecutionException, InterruptedException {

      return dbFirestore.collection("users").document(userId).get().get().exists();
   }
}
