package io.paradaux.treasury.api;

/**
 * TEST-ONLY placeholder for the Treasury API type. The real {@code treasury-api} is a
 * {@code compileOnly} project dependency that is deliberately absent from ChestShop's <em>test
 * runtime</em> classpath. The economy/business service interfaces reference this type in their
 * {@code bind(...)} signatures, so the JUnit Platform discovery scan — which calls
 * {@code Class#getMethods()} on every test-source class — would abort with
 * {@code NoClassDefFoundError} the moment it reached a hand-written service double. Providing this
 * minimal stub on the test classpath lets those signatures resolve during discovery. It is never
 * exercised: no test instantiates the real {@code EconomyServiceImpl}, so no Treasury method is
 * ever invoked through it.
 *
 * <p>The cleaner fix is a one-line test-scope dependency in {@code chestshop/build.gradle.kts}
 * ({@code testRuntimeOnly(project(":treasury:treasury-api")) { isTransitive = false }}); this stub
 * exists only because that build file is out of scope for the current change.
 */
public interface TreasuryApi {
}
