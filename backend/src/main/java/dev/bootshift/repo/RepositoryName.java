package dev.bootshift.repo;

import java.util.regex.Pattern;

/** A GitHub {@code owner/name}, validated so it can never smuggle in a path or URL. */
public record RepositoryName(String owner, String name) {

    /** GitHub limits: owner up to 39 chars of letters, digits and hyphens; name up to 100 of letters, digits, '.', '_', '-'. */
    public static final String PATTERN = "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9._-]{1,100}$";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    public RepositoryName {
        String full = owner + "/" + name;
        if (!COMPILED.matcher(full).matches() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Not a valid GitHub repository name: " + full);
        }
    }

    public static RepositoryName parse(String fullName) {
        int slash = fullName.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Expected owner/name, got: " + fullName);
        }
        return new RepositoryName(fullName.substring(0, slash), fullName.substring(slash + 1));
    }

    @Override
    public String toString() {
        return owner + "/" + name;
    }
}
