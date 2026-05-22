package de.augmentia.strandsagents.examples.tools;

import de.augmentia.strandsagents.core.hook.AgentHook;
import de.augmentia.strandsagents.core.hook.HookContexts;
import de.augmentia.strandsagents.core.hook.HookResult;
import java.util.concurrent.ThreadLocalRandom;

public class RandomTimeoutHook implements AgentHook {

    private final double timeoutProbability;
    private final long maxSleepMs;
    private final double throwProbability;
    private final double cancelProbability;

    public RandomTimeoutHook() {
        this(0.3, 15_000, 0.1, 0.1);
    }

    public RandomTimeoutHook(double timeoutProbability, long maxSleepMs,
                             double throwProbability, double cancelProbability) {
        this.timeoutProbability = timeoutProbability;
        this.maxSleepMs = maxSleepMs;
        this.throwProbability = throwProbability;
        this.cancelProbability = cancelProbability;
    }

    @Override
    public String name() {
        return "random-timeout";
    }

    @Override
    public HookResult beforeToolCall(HookContexts.BeforeToolCallContext ctx) {
        var r = ThreadLocalRandom.current().nextDouble();

        if (r < throwProbability) {
            throw new RuntimeException("Hook: random failure in beforeToolCall");
        }
        if (r < throwProbability + cancelProbability) {
            return new HookResult.Cancel("Hook randomly cancelled tool '" + ctx.toolName() + "'");
        }
        if (r < throwProbability + cancelProbability + timeoutProbability) {
            var sleepMs = (long) (ThreadLocalRandom.current().nextDouble() * maxSleepMs);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new HookResult.Cancel("Hook interrupted during sleep");
            }
        }
        return new HookResult.Continue();
    }

    @Override
    public HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String result) {
        if (!ctx.isError() && ThreadLocalRandom.current().nextDouble() < throwProbability) {
            return new HookResult.Modify<>("Hook modified: " + result);
        }
        return new HookResult.Continue();
    }
}
