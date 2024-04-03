package com.telegrambotapi;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class TelegramBotApiApplication {

//   private static Storage storage;

   public static void main(String[] args) throws IOException {
      String pathToServiceAccountKey = "path/to/serviceAccountKey.json";

      // ==== TELEGRAM BOT LOADER
      ClassLoader classLoader = TelegramBotApiApplication.class.getClassLoader();

      // ==== STREAM CONNECT TO FIREBASE
      File file = new File(Objects.requireNonNull(classLoader.getResource(pathToServiceAccountKey)).getFile());
      FileInputStream serviceAccount =
              new FileInputStream(file.getAbsoluteFile());

      // ==== CONNECT TO FIREBASE
      FirebaseOptions options = new FirebaseOptions.Builder()
              .setCredentials(GoogleCredentials.fromStream(serviceAccount))
              .setDatabaseUrl("https://umatch-ef0db-default-rtdb.europe-west1.firebasedatabase.app/")
              .setStorageBucket("umatch-ef0db.appspot.com")
              .build();

      // ==== STREAM CONNECT TO STORAGE
//      File fileBucket = new File(Objects.requireNonNull(classLoader.getResource(pathToServiceAccountKey)).getFile());
//      FileInputStream serviceAccountBucket =
//              new FileInputStream(fileBucket.getAbsoluteFile());

      // ==== CONNECT TO STORAGE
//      Storage storage = StorageOptions.newBuilder()
//              .setProjectId("umatch-ef0db")
//              .setCredentials(GoogleCredentials.fromStream(serviceAccountBucket))
//              .build().getService();

      // ==== FIREBASE INITIALIZE
      FirebaseApp.initializeApp(options);

      // GET BUCKETS LIST
//      storage.list().iterateAll().forEach(bucketExpected -> System.out.println(bucketExpected.getName()));

      // ==== START APPLICATION
      SpringApplication.run(TelegramBotApiApplication.class, args);
   }


   @Bean
   public Executor taskExecutor() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(2);
      executor.setMaxPoolSize(2);
      executor.setQueueCapacity(500);
      executor.setThreadNamePrefix("ThreadPoolTaskExecutor := ");
      executor.initialize();
      return executor;
   }
}
