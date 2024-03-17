package com.telegrambotapi.aspect.logging;

import com.telegrambotapi.exception.LogException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class LoggingAspectService {
   private static final Logger log = LoggerFactory.getLogger(LoggingAspectService.class);

   @Around("execution(* com.telegrambotapi.service..*(..)))")
   public Object mdcServiceController(final ProceedingJoinPoint joinPoint) throws Throwable {
      String queryMethod = joinPoint.getSignature().getName();
      Object[] args = joinPoint.getArgs();
      logBeforeServiceQuery(queryMethod, args);
      long startTime = System.currentTimeMillis();

      try {
         Object result = joinPoint.proceed();
         logAfterServiceQuery(queryMethod, args, result, startTime);
         return result;
      } catch (Exception ex) {
         logAndGetErrorMessage(queryMethod, args, ex, startTime);
         throw ex;
      }
   }

   private void logBeforeServiceQuery(final String queryMethod, final Object[] args) {
      MDCFields.SERVICE_STEP.putMdcField("SERVICE_IN");
      MDCFields.SERVICE_METHOD.putMdcFieldWithFieldName(queryMethod);
      String argsAsString = Arrays.toString(args);
      log.info("args={};", argsAsString);
      MDCFields.SERVICE_METHOD.removeMdcField();
      MDCFields.SERVICE_STEP.removeMdcField();
   }

   private void logAfterServiceQuery(final String queryMethod, final Object[] args, final Object result, final long startTime) {
      long callTime = System.currentTimeMillis() - startTime;
      String resultInfo = LogUtils.getDaoResultLogInfo(log, result);
      MDCFields.SERVICE_STEP.putMdcField("SERVICE_OUT");
      MDCFields.SERVICE_METHOD.putMdcFieldWithFieldName(queryMethod);
      MDCFields.SERVICE_TIME.putMdcFieldWithFieldName(callTime);
      String argsAsString = Arrays.toString(args);
      log.info(
              "args={}; RESULT: [{}]",
              argsAsString,
              resultInfo
      );
      MDCFields.SERVICE_TIME.removeMdcField();
      MDCFields.SERVICE_METHOD.removeMdcField();
      MDCFields.SERVICE_STEP.removeMdcField();
   }

   private void logAndGetErrorMessage(final String queryMethod, final Object[] args, final Exception ex, final long startTime) {
      long callTime = System.currentTimeMillis() - startTime;
      String errorMsg = String.format(
              "args=%s;",
              Arrays.toString(args)
      );

      MDCFields.DAO_STEP.putMdcField("DAO_ERROR");
      MDCFields.DAO_METHOD.putMdcFieldWithFieldName(queryMethod);
      MDCFields.DAO_TIME.putMdcFieldWithFieldName(callTime);
      log.error(errorMsg, ex);
      MDCFields.DAO_TIME.removeMdcField();
      MDCFields.DAO_METHOD.removeMdcField();
      MDCFields.DAO_STEP.removeMdcField();
      throw new LogException(ex.getMessage(), ex);
   }
}

