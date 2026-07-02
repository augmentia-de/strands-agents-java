package de.augmentia.strandsagents.secrets.aws;

import de.augmentia.strandsagents.config.secrets.SecretProvider;
import de.augmentia.strandsagents.config.secrets.SecretProviderFactory;
import java.util.Map;

public class AwsSecretProviderFactory implements SecretProviderFactory {

    @Override
    public String type() {
        return "aws";
    }

    @Override
    public SecretProvider create(Map<String, String> config) {
        var ssmPath = config.getOrDefault("ssmPath", "");
        return new AwsSsmProvider(ssmPath);
    }
}
