package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

final class AiStudioErrorInfo {

  private static final String ERROR_INFO_TYPE = "type.googleapis.com/google.rpc.ErrorInfo";

  private static final String API_KEY_INVALID = "API_KEY_INVALID";

  private AiStudioErrorInfo() {}

  static boolean isApiKeyInvalid(Gson gson, byte[] body) {
    for (JsonObject detail : AiStudioErrorDetails.ofType(gson, body, ERROR_INFO_TYPE)) {
      if (API_KEY_INVALID.equals(AiStudioErrorDetails.text(detail, "reason"))) {
        return true;
      }
    }
    return false;
  }
}
