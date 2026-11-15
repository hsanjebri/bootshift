package dev.bootshift.build;

/** Which build of a run this is (README section 9). */
public enum BuildKind {
    /** The untouched project. */
    BASELINE,
    /** After the OpenRewrite recipes. */
    RECIPE,
    /** After a repair iteration. */
    REPAIR,
    /** The last verification before review. */
    FINAL
}
