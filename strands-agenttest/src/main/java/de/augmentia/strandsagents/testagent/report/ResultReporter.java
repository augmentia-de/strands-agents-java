package de.augmentia.strandsagents.testagent.report;

import java.util.List;

public class ResultReporter {

    private ResultReporter() {}

    public static void generate(List<TestResult> results) {
        long passed = results.stream().filter(TestResult::passed).count();
        long failed = results.size() - passed;

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  TEST-REPORT");
        System.out.println("=".repeat(60));
        System.out.println("  Total:  " + results.size());
        System.out.println("  Passed: " + passed);
        System.out.println("  Failed: " + failed);
        System.out.println("-".repeat(60));

        for (var r : results) {
            var status = r.passed() ? "  PASS" : "  FAIL";
            System.out.println(status + "  variant " + r.variant()
                + "  " + r.label());
            if (!r.passed()) {
                System.out.println("       error: " + r.error());
                System.out.println("       stopReason: " + r.stopReason());
                System.out.println("       duration: " + r.durationMs() + "ms");
            }
        }
        System.out.println("-".repeat(60));
        System.out.println("  Report written to: test-results.yaml");
        System.out.println();
    }
}
