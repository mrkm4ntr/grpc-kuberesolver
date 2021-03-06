package com.github.mrkm4ntr.kuberesolver;

import com.google.common.base.Preconditions;
import io.fabric8.kubernetes.client.Config;
import io.grpc.Attributes;
import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;

import javax.annotation.Nullable;
import java.net.URI;

public class KubernetesNameResolverProvider extends NameResolverProvider {

    private static final String SCHEME = "k8s";
    private final Config config;

    public KubernetesNameResolverProvider() {
        this.config = Config.autoConfigure(null);
    }

    public KubernetesNameResolverProvider(Config config) {
        this.config = config;
    }


    @Override
    protected boolean isAvailable() {
        return true;
    }

    @Override
    protected int priority() {
        return 5;
    }

    @Nullable
    @Override
    public NameResolver newNameResolver(URI targetUri, Attributes params) {
        if (SCHEME.equals(targetUri.getScheme())) {
            String targetPath = Preconditions.checkNotNull(targetUri.getPath(), "targetPath");
            Preconditions.checkArgument(targetPath.startsWith("/"),
                    "the path component (%s) of the target (%s) must start with '/'", targetPath, targetUri);

            String[] parts = targetPath.split("/");
            if (parts.length != 4) {
                throw new IllegalArgumentException("Must be formatted like kubernetes:///{namespace}/{service}/{port}");
            }

            try {
                int port = Integer.valueOf(parts[3]);
                return new KubernetesNameResolver(config, parts[1], parts[2], port);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unable to parse port number", e);
            }
        } else {
            return null;
        }
    }

    @Override
    public String getDefaultScheme() {
        return SCHEME;
    }
}
