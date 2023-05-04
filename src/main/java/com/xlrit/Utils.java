package com.xlrit;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class Utils {
 
  public static void log(String message) {
    String neutral = "\u001b[0m";
    String blue = "\u001b[96m";
    String red = "\u001b[101m";
    String green = "\u001b[102m";
    String yellow = "\u001b[103m";
    String message_with_color = message
            .replace("[INFO]", blue + "[INFO]" + neutral)
            .replace("[WARNING]", yellow + "[WARNING]" + neutral)
            .replace("[ERROR]", red + "[ERROR]" + neutral)
            .replace("[SUCCESS]", green + "[SUCCESS]" + neutral);
    SimpleDateFormat formatter       = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    Date currentDateTime = new Date(System.currentTimeMillis());
    System.out.println(formatter.format(currentDateTime) + " - " + message_with_color + " ( path: " + methodPath() + " )");
  }

  private static String methodPath() {
    List<String> methods = Arrays.stream(Thread.currentThread().getStackTrace())
            .skip(3)
            .filter(ste -> ste.getClassName().startsWith("com.xlrit"))
            .map(ste -> ste.getMethodName())
            .collect(Collectors.toList());
    Collections.reverse(methods);
    return String.join(" -> ", methods );
  }
}
