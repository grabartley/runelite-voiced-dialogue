package com.grahambartley.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.engine.StdioProtocol.Request;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Framing conformance for the {@code --stdio} protocol: a request decodes to the text, speed, and
 * explicit speaker id the engine renders, and a synthesized frame round-trips through the header +
 * little-endian float32 encoding the plugin's {@code ExternalEngineClient} expects. This runs
 * without the native model so it is part of the normal test suite on every runner.
 */
public class StdioProtocolTest {

  @Test
  public void decodesTextSpeedAndSpeakerId() {
    Request req =
        StdioProtocol.decodeRequest(
            "{\"text\":\"hello there\",\"voice\":{\"race\":\"ELF\",\"gender\":\"FEMALE\",\"player\":false},\"emotion\":\"HAPPY\",\"speed\":1.0,\"speakerId\":21}");
    assertEquals("hello there", req.text);
    assertEquals(1.0f, req.speed, 0.0001f);
    assertEquals(21, req.speakerId());
  }

  @Test
  public void absentSpeedDefaultsToOne() {
    // Absent speed defaults to 1.0 so the engine never feeds a zero rate to sherpa-onnx.
    Request req =
        StdioProtocol.decodeRequest(
            "{\"text\":\"my move\",\"speakerId\":24,\"emotion\":\"ANGRY\"}");
    assertEquals(24, req.speakerId());
    assertEquals(1.0f, req.speed, 0.0001f);
  }

  @Test
  public void voiceObjectIsIgnored() {
    // The plugin owns voice selection and sends the resolved speaker id; the engine renders that id
    // and never maps the voice object's race/gender to a voice itself.
    Request req =
        StdioProtocol.decodeRequest(
            "{\"text\":\"x\",\"voice\":{\"race\":\"BANANA\",\"gender\":\"MALE\",\"player\":false},\"speakerId\":25}");
    assertEquals(25, req.speakerId());
  }

  @Test
  public void encodesSamplesAsLittleEndianFloat32() {
    float[] samples = {0f, 1f, -1f, 0.5f};
    byte[] frame = StdioProtocol.encodeSamples(samples);
    assertEquals(samples.length * 4, frame.length);

    ByteBuffer buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
    float[] decoded = new float[samples.length];
    for (int i = 0; i < samples.length; i++) {
      decoded[i] = buf.getFloat();
    }
    assertArrayEquals(samples, decoded, 0.0f);
  }

  @Test
  public void writesHeaderLineThenPcmFrame() throws Exception {
    float[] samples = {0.25f, -0.25f};
    byte[] pcm = StdioProtocol.encodeSamples(samples);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    StdioProtocol.writeResponse(out, 24000, pcm);

    byte[] written = out.toByteArray();
    String asText = new String(written, StandardCharsets.UTF_8);
    int newline = asText.indexOf('\n');
    assertTrue("response must contain a header line", newline >= 0);

    String header = asText.substring(0, newline).trim();
    assertTrue(header.contains("\"sampleRate\":24000"));
    assertTrue(header.contains("\"samples\":2"));
    assertTrue(header.contains("\"format\":\"f32le\""));

    // The bytes after the header line are exactly the PCM frame.
    int frameStart = newline + 1;
    byte[] tail = new byte[written.length - frameStart];
    System.arraycopy(written, frameStart, tail, 0, tail.length);
    assertArrayEquals(pcm, tail);
  }

  @Test
  public void explicitSpeakerIdIsRendered() {
    Request req = StdioProtocol.decodeRequest("{\"text\":\"hi\",\"speakerId\":17}");
    assertEquals(17, req.speakerId());
  }

  @Test
  public void absentSpeakerIdFallsBackToDefault() {
    Request req = StdioProtocol.decodeRequest("{\"text\":\"hi\"}");
    assertEquals(StdioProtocol.NO_SPEAKER_ID, req.explicitSpeakerId);
    assertEquals(StdioProtocol.DEFAULT_SPEAKER, req.speakerId());
  }

  @Test
  public void negativeSpeakerIdIsTreatedAsAbsent() {
    Request req = StdioProtocol.decodeRequest("{\"text\":\"hi\",\"speakerId\":-1}");
    assertEquals(StdioProtocol.DEFAULT_SPEAKER, req.speakerId());
  }
}
