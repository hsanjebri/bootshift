package dev.bootshift.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads Surefire and Failsafe XML reports from every module of a working copy.
 *
 * <p>The reports are written by untrusted build code, so parsing is defensive: no DTDs or
 * external entities, symlinks are not followed, and oversized or excess files are skipped.
 */
public final class SurefireReports {

    static final long MAX_REPORT_BYTES = 20L * 1024 * 1024;
    static final int MAX_REPORTS = 5_000;
    static final int MAX_DEPTH = 12;

    private static final Logger log = LoggerFactory.getLogger(SurefireReports.class);
    private static final Set<String> REPORT_DIRS = Set.of("surefire-reports", "failsafe-reports");
    private static final Set<String> SKIPPED_DIRS = Set.of(".git", "node_modules", ".mvn", "src");

    /** Totals across all reports. {@code failed} counts failures and errors. */
    public record Summary(int run, int failed, int skipped, List<Failure> failures) {

        static final Summary EMPTY = new Summary(0, 0, 0, List.of());
    }

    private SurefireReports() {
    }

    public static Summary read(Path repository) throws IOException {
        Path root = repository.toRealPath();
        List<Path> reports = find(root);
        int run = 0;
        int failed = 0;
        int skipped = 0;
        List<Failure> failures = new ArrayList<>();
        for (Path report : reports) {
            String module = module(root, report);
            try (InputStream in = Files.newInputStream(report, LinkOption.NOFOLLOW_LINKS)) {
                Summary summary = parse(in, module);
                run += summary.run();
                failed += summary.failed();
                skipped += summary.skipped();
                failures.addAll(summary.failures());
            } catch (XMLStreamException e) {
                log.warn("Skipping unreadable test report {}: {}", root.relativize(report), e.getMessage());
            }
        }
        return new Summary(run, failed, skipped, List.copyOf(failures));
    }

    /** Parses one {@code TEST-*.xml} report. Counts test cases rather than trusting the suite's totals. */
    static Summary parse(InputStream xml, String module) throws XMLStreamException {
        XMLStreamReader reader = factory().createXMLStreamReader(xml);
        try {
            int run = 0;
            int failed = 0;
            int skipped = 0;
            List<Failure> failures = new ArrayList<>();
            String className = null;
            String testName = null;
            boolean caseFailed = false;
            boolean caseSkipped = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "testcase" -> {
                            className = attribute(reader, "classname");
                            testName = attribute(reader, "name");
                            caseFailed = false;
                            caseSkipped = false;
                        }
                        case "failure", "error" -> {
                            if (testName != null && !caseFailed) {
                                caseFailed = true;
                                failures.add(testFailure(module, className, testName,
                                        attribute(reader, "type"), attribute(reader, "message"), reader));
                            }
                        }
                        case "skipped" -> caseSkipped = testName != null;
                        default -> {
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "testcase".equals(reader.getLocalName())) {
                    run++;
                    if (caseFailed) {
                        failed++;
                    } else if (caseSkipped) {
                        skipped++;
                    }
                    testName = null;
                }
            }
            return new Summary(run, failed, skipped, failures);
        } finally {
            reader.close();
        }
    }

    private static Failure testFailure(String module, @Nullable String className, String testName,
                                       @Nullable String type, @Nullable String message, XMLStreamReader reader) throws XMLStreamException {
        String detail = text(reader);
        String headline = message != null && !message.isBlank() ? message : (type != null ? type : detail);
        boolean startup = contains(headline, "ApplicationContext") || contains(detail, "Failed to load ApplicationContext");
        String shortType = type == null ? null : type.substring(type.lastIndexOf('.') + 1);
        String full = shortType == null || headline.startsWith(shortType) || headline.equals(type) ? headline : shortType + ": " + headline;
        return Failure.of(startup ? FailureType.STARTUP : FailureType.TEST, module, className, null, testName, full);
    }

    /** Reads the element's text (the stack trace) without loading more than needed. */
    private static String text(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        int depth = 1;
        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> depth++;
                case XMLStreamConstants.END_ELEMENT -> depth--;
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (text.length() < 4_000) {
                        text.append(reader.getText());
                    }
                }
                default -> {
                }
            }
        }
        return text.toString();
    }

    private static List<Path> find(Path root) throws IOException {
        List<Path> reports = new ArrayList<>();
        Files.walkFileTree(root, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return !dir.equals(root) && SKIPPED_DIRS.contains(dir.getFileName().toString())
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path parent = file.getParent();
                String name = file.getFileName().toString();
                if (attrs.isRegularFile() && name.startsWith("TEST-") && name.endsWith(".xml")
                        && REPORT_DIRS.contains(parent.getFileName().toString())
                        && "target".equals(parent.getParent().getFileName().toString())) {
                    if (attrs.size() > MAX_REPORT_BYTES) {
                        log.warn("Skipping oversized test report {}", root.relativize(file));
                    } else if (reports.size() < MAX_REPORTS) {
                        reports.add(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
        reports.sort(null);
        return reports;
    }

    /** {@code root/a/b/target/surefire-reports/TEST-x.xml} belongs to module {@code a/b}. */
    static String module(Path root, Path report) {
        Path moduleDir = report.getParent().getParent().getParent();
        String relative = root.relativize(moduleDir).toString().replace('\\', '/');
        return relative.isEmpty() ? "." : relative;
    }

    static XMLInputFactory factory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    private static @Nullable String attribute(XMLStreamReader reader, String name) {
        return reader.getAttributeValue(null, name);
    }

    private static boolean contains(@Nullable String text, String needle) {
        return text != null && text.contains(needle);
    }
}
