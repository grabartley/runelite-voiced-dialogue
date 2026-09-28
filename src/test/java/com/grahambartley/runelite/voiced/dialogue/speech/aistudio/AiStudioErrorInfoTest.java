package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import org.junit.Test;

public class AiStudioErrorInfoTest {

  private final Gson gson = new Gson();

  private boolean isApiKeyInvalid(String body) {
    return AiStudioErrorInfo.isApiKeyInvalid(gson, body.getBytes(UTF_8));
  }

  @Test
  public void theApiKeyInvalidReasonIsRead() {
    assertTrue(isApiKeyInvalid(AiStudioResponses.invalidApiKey()));
  }

  @Test
  public void aRejectionWithoutErrorInfoIsNotAKeyProblem() {
    assertFalse(isApiKeyInvalid(AiStudioResponses.noMatchingVoice()));
  }

  @Test
  public void anotherErrorInfoReasonIsNotAKeyProblem() {
    assertFalse(isApiKeyInvalid(AiStudioResponses.invalidArgument("bad", "SOMETHING_ELSE")));
  }

  @Test
  public void otherDetailTypesAreIgnored() {
    assertFalse(isApiKeyInvalid(AiStudioResponses.statedRetryDelay("5s")));
  }

  @Test
  public void anUnreadableOrMissingBodyIsNotAKeyProblem() {
    assertFalse(isApiKeyInvalid("not json"));
    assertFalse(isApiKeyInvalid(""));
    assertFalse(AiStudioErrorInfo.isApiKeyInvalid(gson, null));
  }
}
