package dev.bootshift.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jspecify.annotations.Nullable;

/**
 * Chooses the sandbox JDK for a project from its root {@code pom.xml}: the smallest available
 * JDK that is at least the declared Java version. Spring Boot 3.5 defaults to Java 17.
 */
public final class JavaVersions {

    static final int DEFAULT = 17;
    static final List<Integer> AVAILABLE = List.of(17, 21, 25);

    /** Checked in order; the first one that resolves to a number wins. */
    private static final List<String> PROPERTIES = List.of(
            "maven.compiler.release", "java.version", "maven.compiler.target", "maven.compiler.source");
    private static final Pattern REFERENCE = Pattern.compile("^\\$\\{([^}]+)}$");
    private static final long MAX_POM_BYTES = 2L * 1024 * 1024;

    /**
     * @param declared the Java version the project asks for, if found
     * @param jdk      the sandbox JDK to build with
     */
    public record Selection(OptionalInt declared, int jdk) {
    }

    /** The project needs a newer Java than the sandbox has. */
    public static final class UnsupportedJava extends RuntimeException {
        public UnsupportedJava(int version) {
            super("The project targets Java " + version + "; the sandbox supports up to Java " + AVAILABLE.getLast());
        }
    }

    private JavaVersions() {
    }

    public static Selection select(Path repository) throws IOException {
        Path pom = repository.resolve("pom.xml");
        if (!Files.isRegularFile(pom, LinkOption.NOFOLLOW_LINKS) || Files.size(pom) > MAX_POM_BYTES) {
            return new Selection(OptionalInt.empty(), DEFAULT);
        }
        try (InputStream in = Files.newInputStream(pom, LinkOption.NOFOLLOW_LINKS)) {
            return select(declared(properties(in)));
        } catch (XMLStreamException e) {
            return new Selection(OptionalInt.empty(), DEFAULT);
        }
    }

    static Selection select(OptionalInt declared) {
        if (declared.isEmpty()) {
            return new Selection(declared, DEFAULT);
        }
        int version = declared.getAsInt();
        for (int jdk : AVAILABLE) {
            if (jdk >= version) {
                return new Selection(declared, jdk);
            }
        }
        throw new UnsupportedJava(version);
    }

    static OptionalInt declared(Map<String, String> properties) {
        for (String name : PROPERTIES) {
            OptionalInt version = parse(resolve(properties.get(name), properties, 0));
            if (version.isPresent()) {
                return version;
            }
        }
        return OptionalInt.empty();
    }

    /** {@code 17}, {@code 1.8} and {@code 21.0.2} give 17, 8 and 21. */
    static OptionalInt parse(@Nullable String value) {
        if (value == null) {
            return OptionalInt.empty();
        }
        Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d{1,2})(?:\\.\\d+)*$").matcher(value.strip());
        return matcher.matches() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
    }

    private static @Nullable String resolve(@Nullable String value, Map<String, String> properties, int depth) {
        if (value == null || depth > 5) {
            return value;
        }
        Matcher reference = REFERENCE.matcher(value.strip());
        return reference.matches() ? resolve(properties.get(reference.group(1)), properties, depth + 1) : value;
    }

    /** The {@code <project><properties>} section. Profiles and parents are not considered. */
    static Map<String, String> properties(InputStream pom) throws XMLStreamException {
        XMLStreamReader reader = SurefireReports.factory().createXMLStreamReader(pom);
        Map<String, String> properties = new HashMap<>();
        try {
            int depth = 0;
            boolean inProperties = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    if (depth == 2 && "properties".equals(reader.getLocalName())) {
                        inProperties = true;
                    } else if (inProperties && depth == 3) {
                        String name = reader.getLocalName();
                        properties.put(name, reader.getElementText().strip());
                        depth--;
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (depth == 2 && inProperties) {
                        inProperties = false;
                    }
                    depth--;
                }
            }
            return properties;
        } finally {
            reader.close();
        }
    }
}
