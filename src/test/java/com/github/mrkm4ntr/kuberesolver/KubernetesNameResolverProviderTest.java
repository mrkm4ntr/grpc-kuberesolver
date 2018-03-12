package com.github.mrkm4ntr.kuberesolver;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.util.RoundRobinLoadBalancerFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class KubernetesNameResolverProviderTest {

    private static final int DEFAULT_PORT = 9090;

    @Rule
    public KubernetesServer k8sServer = new KubernetesServer(false);

    @Test
    public void simpleTest() throws Exception {
        Server server = ServerBuilder.forPort(DEFAULT_PORT)
                .addService(new CounterGrpc.CounterImplBase() {
                    @Override
                    public void countIt(com.github.mrkm4ntr.kuberesolver.Test.Count request,
                                        StreamObserver<com.github.mrkm4ntr.kuberesolver.Test.Counted> responseObserver) {
                        responseObserver.onNext(com.github.mrkm4ntr.kuberesolver.Test.Counted.newBuilder().setCounter(1).build());
                        responseObserver.onCompleted();
                    }
                }).build();
        server.start();
        System.out.println("server listening...");

        k8sServer.expect().withPath("/api/v1/namespaces/default/endpoints/test")
                .andReturn(200, new EndpointsBuilder().withSubsets(
                        new EndpointSubsetBuilder()
                                .withAddresses(new EndpointAddress(null, "127.0.0.1", null, null))
                                .withPorts(new EndpointPort("test", DEFAULT_PORT, "tcp"))
                                .build())
                        .build())
                .once();
        Config config = new ConfigBuilder(k8sServer.getClient().getConfiguration()).build();
        ManagedChannel channel = ManagedChannelBuilder.forTarget("k8s:///default/test/" + DEFAULT_PORT)
                .nameResolverFactory(new KubernetesNameResolverProvider(config))
                .usePlaintext(true)
                .loadBalancerFactory(RoundRobinLoadBalancerFactory.getInstance()).build();
        CounterGrpc.CounterBlockingStub stub = CounterGrpc.newBlockingStub(channel);

        com.github.mrkm4ntr.kuberesolver.Test.Counted counted =
                stub.countIt(com.github.mrkm4ntr.kuberesolver.Test.Count.newBuilder().build());
        Assert.assertEquals(1, counted.getCounter());

        channel.shutdown();
        server.shutdown();
    }
}
