package com.telegrambotapi.constants;

public class BotConstants {

   public static final String AUTHORIZATION = "Authorization";
   public static final String MODEL = "model";
   public static final String PROMPT = "prompt";
   public static final String TEMPERATURE = "temperature";
   public static final String MAX_TOKENS = "max_tokens";
   public static final String TOP_P = "top_p";
   public static final String FREQUENCY_PENALTY = "frequency_penalty";
   public static final String PRESENCE_PENALTY = "presence_penalty";
   public static final String YES_BUTTON = "YES_BUTTON";
   public static final String NO_BUTTON = "NO_BUTTON";
   public static final String ERROR_TEXT = "Error occurred: ";

   public static final String HELLO_ASSISTANT = "Ты мой персональный ассистент. " +
           "Отвечай простым разговорным языком и короткими предложениями. А теперь поприветствуй меня!";
   public static final String REJECTION_ASSISTANT = "Если захотите задать вопрос личному ассистенту вы можете перейти в меню\n" +
           "Или просто нажать здесь /assistant";

   public static final String HELP_TEXT = "Вы можете выполнить команды из основного меню слева или введя команду вручную:\n\n" +
           "Введите /start, чтобы увидеть приветственное сообщение\n\n" +
           "Введите /assistant, чтобы начать чат с личным ассистентом\n\n" +
           "Введите /help, чтобы снова увидеть это сообщение";

   public static final String BIRTHDAY_REGEX = "^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/\\d{4}$";
   public static final String BIRTHDAY_FORMAT = "Введите вашу дату рождения в формате:\n" +
           "DD/MM/YYYY";
   public static final String FIRST_GREETING = "Как я могу к вам обращаться:";
   public static final String BIRTHDAY_EXCEPT = "Спасибо! Ваше имя и день рождения записаны.";
   public static final String BIRTHDAY_ERROR = "Извините, команда не была распознана. Пожалуйста, введите дату рождения в формате\n" +
           "DD/MM/YYYY";

}
