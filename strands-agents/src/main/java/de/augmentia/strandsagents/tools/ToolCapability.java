package de.augmentia.strandsagents.tools;

import de.augmentia.strandsagents.interceptor.security.CapabilityToken;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolCapability {
    CapabilityToken value();
}
