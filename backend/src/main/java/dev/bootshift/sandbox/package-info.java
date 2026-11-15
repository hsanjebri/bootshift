/**
 * Sandbox containers: one throwaway, locked-down Docker container per build (README section 13).
 * The sandbox receives no secrets and reaches the network only through the dependency proxy.
 * Must not depend on {@code dev.bootshift.github}.
 */
@NullMarked
package dev.bootshift.sandbox;

import org.jspecify.annotations.NullMarked;
