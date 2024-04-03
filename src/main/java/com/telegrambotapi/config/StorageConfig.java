package com.telegrambotapi.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import com.telegrambotapi.TelegramBotApiApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;

@Configuration
public class StorageConfig {

   @Bean
   public Storage storage() throws IOException {
      String pathToServiceAccountKey = "path/to/serviceAccountKey.json";

      ClassLoader classLoader = TelegramBotApiApplication.class.getClassLoader();
      File file = new File(Objects.requireNonNull(classLoader.getResource(pathToServiceAccountKey)).getFile());
      FileInputStream serviceAccount =
              new FileInputStream(file.getAbsoluteFile());

      return StorageOptions.newBuilder()
              .setProjectId("umatch-ef0db")
              .setCredentials(GoogleCredentials.fromStream(serviceAccount))
              .build()
              .getService();
   }
}
