package com.grahambartley.runelite.voiced.dialogue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NoCommentsRuleTest {

  private static final String CONFIG_PATH = "config/checkstyle/checkstyle.xml";
  private static final String FIXTURE_RESOURCE = "/comment-gate/CommentGateFixture.java.txt";
  private static final String RULE_ID = "NoComments";
  private static final String STANDARD_DOC = "docs/development.md";

  private static final Map<Integer, String> COMMENT_LINES = commentLines();
  private static final Map<Integer, String> COMMENT_LIKE_LITERAL_LINES = commentLikeLiteralLines();

  @ClassRule public static final TemporaryFolder WORKSPACE = new TemporaryFolder();

  private static List<AuditEvent> violations;

  private static Map<Integer, String> commentLines() {
    Map<Integer, String> lines = new LinkedHashMap<>();
    lines.put(3, "javadoc on the type");
    lines.put(6, "a plain line comment");
    lines.put(19, "a trailing comment after code");
    lines.put(21, "a bare double slash carrying no text");
    lines.put(23, "a block comment");
    lines.put(25, "an inline block comment after code");
    return Collections.unmodifiableMap(lines);
  }

  private static Map<Integer, String> commentLikeLiteralLines() {
    Map<Integer, String> lines = new LinkedHashMap<>();
    lines.put(7, "a url whose slashes sit inside a string");
    lines.put(9, "block comment markers inside a string");
    lines.put(11, "a double quote character literal");
    lines.put(13, "an escaped quote followed by slashes inside a string");
    lines.put(15, "the opening delimiter of a text block");
    lines.put(16, "comment markers inside a text block");
    lines.put(17, "the closing delimiter of a text block");
    return Collections.unmodifiableMap(lines);
  }

  @BeforeClass
  public static void auditTheFixture() throws Exception {
    File config = new File(CONFIG_PATH);
    assertTrue(
        CONFIG_PATH + " is missing, so the gate has no configuration to enforce", config.isFile());
    violations = audit(config, fixtureWrittenToDisk());
  }

  @Test
  public void everyCommentFormIsReported() {
    Set<Integer> reported = reportedLines();
    List<String> missed = new ArrayList<>();
    for (Map.Entry<Integer, String> line : COMMENT_LINES.entrySet()) {
      if (!reported.contains(line.getKey())) {
        missed.add("line " + line.getKey() + " (" + line.getValue() + ")");
      }
    }
    assertEquals(
        "the gate failed to report comment forms it must catch, so it has stopped gating",
        Collections.emptyList(),
        missed);
  }

  @Test
  public void noCommentLikeStringLiteralIsMistakenForAComment() {
    Set<Integer> reported = reportedLines();
    List<String> wronglyReported = new ArrayList<>();
    for (Map.Entry<Integer, String> line : COMMENT_LIKE_LITERAL_LINES.entrySet()) {
      if (reported.contains(line.getKey())) {
        wronglyReported.add("line " + line.getKey() + " (" + line.getValue() + ")");
      }
    }
    assertEquals(
        "the gate reported source that is not a comment, so the query matches too much",
        Collections.emptyList(),
        wronglyReported);
  }

  @Test
  public void theGateReportsEveryCommentAndNothingElse() {
    assertEquals(
        "expected one violation per comment in the fixture, got them at lines " + reportedLines(),
        COMMENT_LINES.size(),
        violations.size());
  }

  @Test
  public void everyViolationCarriesTheRuleIdSoCiOutputIsTraceable() {
    assertFalse("no violations were produced, so the id proves nothing", violations.isEmpty());
    for (AuditEvent violation : violations) {
      assertEquals(
          "a violation reached the build without the rule id that names it",
          RULE_ID,
          violation.getModuleId());
    }
  }

  @Test
  public void everyViolationNamesTheStandardItEnforces() {
    assertFalse("no violations were produced, so the message proves nothing", violations.isEmpty());
    for (AuditEvent violation : violations) {
      assertTrue(
          "a violation reached the build without pointing at " + STANDARD_DOC,
          violation.getMessage().contains(STANDARD_DOC));
    }
  }

  private static Set<Integer> reportedLines() {
    Set<Integer> lines = new TreeSet<>();
    for (AuditEvent violation : violations) {
      lines.add(violation.getLine());
    }
    return lines;
  }

  private static File fixtureWrittenToDisk() throws Exception {
    File target = new File(WORKSPACE.getRoot(), "CommentGateFixture.java");
    try (InputStream source = NoCommentsRuleTest.class.getResourceAsStream(FIXTURE_RESOURCE)) {
      assertNotNull(FIXTURE_RESOURCE + " is missing from the gate test resources", source);
      Files.copy(source, target.toPath());
    }
    return target;
  }

  private static List<AuditEvent> audit(File config, File subject) throws Exception {
    Configuration configuration =
        ConfigurationLoader.loadConfiguration(
            config.getPath(), new PropertiesExpander(System.getProperties()));
    List<AuditEvent> collected = new ArrayList<>();
    Checker checker = new Checker();
    try {
      checker.setModuleClassLoader(Checker.class.getClassLoader());
      checker.configure(configuration);
      checker.addListener(new ViolationCollector(collected));
      checker.process(Collections.singletonList(subject));
    } finally {
      checker.destroy();
    }
    return collected;
  }

  private static final class ViolationCollector implements AuditListener {

    private final List<AuditEvent> collected;

    private ViolationCollector(List<AuditEvent> collected) {
      this.collected = collected;
    }

    @Override
    public void addError(AuditEvent event) {
      collected.add(event);
    }

    @Override
    public void addException(AuditEvent event, Throwable throwable) {
      throw new IllegalStateException("checkstyle failed to audit the fixture", throwable);
    }

    @Override
    public void auditStarted(AuditEvent event) {}

    @Override
    public void auditFinished(AuditEvent event) {}

    @Override
    public void fileStarted(AuditEvent event) {}

    @Override
    public void fileFinished(AuditEvent event) {}
  }
}
