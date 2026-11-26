/**
 * The Spring Boot wiring for the automation modules.
 *
 * <p>{@link org.sequeless.automation.autoconfigure.AutomationCoreAutoConfiguration} wires the core's action executor
 * and trigger evaluator from the ports the host supplies. It has no property switch. The core is a compile-scope
 * dependency of this module, so it is always on the classpath, and it is wired whenever its ports are present.
 *
 * <p>The property {@code sequeless.automation.adapter} selects one adapter. The value {@code inprocess} selects
 * {@link org.sequeless.automation.autoconfigure.InProcessAutomationAutoConfiguration}, and {@code temporal} selects
 * {@link org.sequeless.automation.autoconfigure.TemporalAutomationAutoConfiguration}. The adapters are optional
 * dependencies of this module. Each autoconfiguration is also guarded by {@code @ConditionalOnClass}, so the module
 * starts with neither adapter on the classpath, and a host gets only the adapter it puts there.
 *
 * <p>These names are public contract. The property name and the entries in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} do not change.
 */
package org.sequeless.automation.autoconfigure;
