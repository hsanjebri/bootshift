package dev.bootshift.build;

/** Failure categories from README section 9. */
public enum FailureType {
    /** javac reported an error. */
    COMPILE,
    /** A test failed or errored. */
    TEST,
    /** An artifact, parent POM or plugin could not be resolved. */
    DEPENDENCY,
    /** A Spring test could not start its application context. */
    STARTUP,
    /** The build failed for another reason, for example a plugin check. */
    OTHER
}
