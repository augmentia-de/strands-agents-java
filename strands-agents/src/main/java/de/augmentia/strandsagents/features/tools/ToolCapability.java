package de.augmentia.strandsagents.features.tools;

import de.augmentia.strandsagents.features.security.CapabilityToken;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolCapability {
    CapabilityToken value();
}
