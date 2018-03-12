package com.github.mrkm4ntr.kuberesolver;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.Status;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class KubernetesNameResolver extends NameResolver {

    private final String namespace;
    private final String name;
    private final int port;
    private final KubernetesClient client;
    private Listener listener;
    private Watch watch = null;

    public KubernetesNameResolver(String namespace, String name, int port) {
        this.namespace = namespace;
        this.name = name;
        this.port = port;
        this.client = new DefaultKubernetesClient(Config.autoConfigure(null));
    }

    public KubernetesNameResolver(Config config, String namespace, String name, int port) {
        this.namespace = namespace;
        this.name = name;
        this.port = port;
        this.client = new DefaultKubernetesClient(config);
    }

    @Override
    public String getServiceAuthority() {
        return client.getMasterUrl().getAuthority();
    }

    @Override
    public void start(Listener listener) {
        this.listener = listener;
        refresh();
    }

    @Override
    public void shutdown() {
        client.close();
    }

    @Override
    public synchronized void refresh() {
        try {
            Endpoints endpoints = client.endpoints().inNamespace(namespace).withName(name).get();
            if (endpoints != null) {
                update(endpoints);
            }
            if (watch != null) {
                watch.close();
            }
            watch();
        } catch (Exception e) {
            listener.onError(Status.fromThrowable(e));
        }
    }

    private void update(Endpoints endpoints) {
        List<EquivalentAddressGroup> servers = endpoints.getSubsets().stream()
                .filter(subset -> subset.getPorts().stream().anyMatch(ep -> ep.getPort() == port))
                .flatMap(subset -> subset.getAddresses().stream().map(address ->
                        new EquivalentAddressGroup(new InetSocketAddress(address.getIp(), port))))
                .collect(Collectors.toList());
        listener.onAddresses(servers, Attributes.EMPTY);
    }

    protected void watch() {
        watch = client.endpoints().inNamespace(namespace)
                .withName(name)
                .watch(new Watcher<Endpoints>() {
                    @Override
                    public void eventReceived(Action action, Endpoints endpoints) {
                        switch (action) {
                            case MODIFIED:
                            case ADDED:
                                update(endpoints);
                                return;
                            case DELETED:
                                // TODO: remove only removed entries
                                listener.onAddresses(Collections.emptyList(), Attributes.EMPTY);
                                return;
                        }
                    }

                    @Override
                    public void onClose(KubernetesClientException e) {
                        // TODO: logging and test
                        watch();
                    }
                });
    }
}
