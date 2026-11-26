/**
 * The automation testkit: contract tests and test doubles for implementations of the automation API.
 *
 * <p>This is a test library. Adapter and core modules depend on it at test scope, and a published
 * artifact carries no runtime behaviour. It holds {@link org.sequeless.automation.testkit.AutomationContract},
 * the abstract JUnit 5 contract every {@link org.sequeless.automation.api.AutomationPort} implementation must
 * pass, and the doubles a contract or an implementation's own test wires against: the recording
 * {@link org.sequeless.automation.testkit.RecordingActionExecutor} and
 * {@link org.sequeless.automation.testkit.RecordingTriggerEvaluator}, and the in-memory
 * {@link org.sequeless.automation.testkit.InMemoryObjectReadPort} and
 * {@link org.sequeless.automation.testkit.InMemoryStoreCommandPort}.
 *
 * <p>The testkit depends on {@code automation-api} and on the kernel and ontology automation types that API
 * exposes, plus JUnit and AssertJ, which the contract uses. It has no dependency on the automation core,
 * the adapters, the Store, or the prototype packages.
 */
package org.sequeless.automation.testkit;
