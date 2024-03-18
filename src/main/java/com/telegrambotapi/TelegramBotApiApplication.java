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

   public static void main(String[] args) throws IOException {
      // ====  TELEGRAM BOT LOADER
      ClassLoader classLoader = TelegramBotApiApplication.class.getClassLoader();

      // ==== CONNECT TO FIREBASE
      File file = new File(Objects.requireNonNull(classLoader.getResource("path/to/serviceAccountKey.json")).getFile());
      FileInputStream serviceAccount =
              new FileInputStream(file.getAbsoluteFile());

      FirebaseOptions options = new FirebaseOptions.Builder()
              .setCredentials(GoogleCredentials.fromStream(serviceAccount))
              .setDatabaseUrl("https://umatch-ef0db-default-rtdb.europe-west1.firebasedatabase.app/")
              .setStorageBucket("umatch-ef0db.appspot.com")
              .build();

//      FirebaseOptions options = new FirebaseOptions.Builder()
//              .setCredentials(GoogleCredentials.fromStream(serviceAccount))
//              .build();

      FirebaseApp.initializeApp(options);

      SpringApplication.run(TelegramBotApiApplication.class, args);
   }

   @Bean
   public Executor taskExecutor() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(2);
      executor.setMaxPoolSize(2);
      executor.setQueueCapacity(500);
      executor.setThreadNamePrefix("GithubLookup-");
      executor.initialize();
      return executor;
   }
}
