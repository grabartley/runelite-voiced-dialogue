package com.grahambartley.runelite.voiced.dialogue.profile;

public final class EmotionResolver {

  private final ExpressionEmotionTable expressionEmotions;

  public EmotionResolver() {
    this(ExpressionEmotionTable.load());
  }

  EmotionResolver(ExpressionEmotionTable expressionEmotions) {
    this.expressionEmotions = expressionEmotions;
  }

  public Emotion resolve(int headAnimationId, boolean enableEmotion) {
    if (!enableEmotion) {
      return Emotion.NEUTRAL;
    }
    return expressionEmotions.resolve(headAnimationId);
  }
}
