package de.augmentia.strandsagents.core.plugin.hitl;

import de.augmentia.strandsagents.core.hook.AgentHook;
import de.augmentia.strandsagents.core.hook.HookContexts;
import de.augmentia.strandsagents.core.hook.HookResult;
import de.augmentia.strandsagents.core.plugin.guardrail.ApprovalResult;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class HITLHook implements AgentHook {

    public enum Mode { SYNC_BLOCKING, ASYNC_PERSIST }

    private final HITLProvider provider;
    private final HITLAuthority authority;
    private final List<String> criticalTools;
    private final Mode mode;

    public HITLHook(HITLProvider provider, HITLAuthority authority) {
        this(provider, authority, List.of(), Mode.SYNC_BLOCKING);
    }

    public HITLHook(HITLProvider provider, HITLAuthority authority, List<String> criticalTools, Mode mode) {
        this.provider = provider;
        this.authority = authority;
        this.criticalTools = criticalTools;
        this.mode = mode;
    }

    @Override
    public String name() {
        return "hitl";
    }

    @Override
    public HookResult beforeToolCall(HookContexts.BeforeToolCallContext ctx) {
        if (authority == HITLAuthority.AUTO) {
            return new HookResult.Continue();
        }
        if (!criticalTools.isEmpty() && !criticalTools.contains(ctx.toolName())) {
            return new HookResult.Continue();
        }

        return switch (mode) {
            case SYNC_BLOCKING -> handleSync(ctx);
            case ASYNC_PERSIST -> handleAsync(ctx);
        };
    }

    private HookResult handleSync(HookContexts.BeforeToolCallContext ctx) {
        var approval = provider.requestApproval(ctx.toolName(), ctx.arguments().toString());
        if (approval.approved()) {
            return new HookResult.Continue();
        }
        return new HookResult.Cancel(approval.feedback() != null ? approval.feedback() : "Rejected by user");
    }

    private HookResult handleAsync(HookContexts.BeforeToolCallContext ctx) {
        return new HookResult.Cancel("Async HITL requires external state persistence");
    }

    public static HITLProvider consoleProvider() {
        return (action, context) -> {
            System.out.print("Tool '" + action + "' with args: " + context + " — execute? [y/N] ");
            var input = new Scanner(System.in).nextLine();
            return input.equalsIgnoreCase("y")
                ? ApprovalResult.approved(action)
                : ApprovalResult.denied(action, "Rejected by console user");
        };
    }
}
