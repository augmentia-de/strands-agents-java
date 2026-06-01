package de.augmentia.strandsagents.core.tools;

import de.augmentia.strandsagents.core.security.CapabilityToken;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolCapability {
    CapabilityToken value();
}
