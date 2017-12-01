/*
Copyright 2017 Echo Park Labs

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

For additional information, contact:

email: info@echoparklabs.io
*/

package com.epl.service.geometry;


import com.esri.core.geometry.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.epl.service.geometry.GeometryOperatorsGrpc.GeometryOperatorsBlockingStub;
import com.epl.service.geometry.GeometryOperatorsGrpc.GeometryOperatorsStub;
import io.grpc.*;
//import io.grpc.grpclb.*;
import io.grpc.internal.SerializingExecutor;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Sample client code that makes gRPC calls to the server.
 */
public class GeometryOperatorsClient {
    // TODO replace
    private final ManagedChannel channel;
    private final GeometryOperatorsBlockingStub blockingStub;
    private final GeometryOperatorsStub asyncStub;
    // TODO replace


    private static final Logger logger = Logger.getLogger(GeometryOperatorsClient.class.getName());

    private final SerializingExecutor channelExecutor = new SerializingExecutor(MoreExecutors.directExecutor());
    private final LinkedList<ManagedChannel> subChannels = new LinkedList<ManagedChannel>();

//    private GrpclbLoadBalancerFactory loadBalancerFactory;
    private LoadBalancer loadBalancer;
    private io.grpc.LoadBalancer.Helper loadBalancerHelper;
    private List<ResolvedServerInfoGroup> grpclbResolutionList;

    private Random random = new Random();
    private TestHelper testHelper;

    private static class WorkerSocketAddress extends SocketAddress {
        final String name;
        final String port;

        WorkerSocketAddress(String host, String port) {
            this.name = host;
            this.port = port;
        }

        @Override
        public String toString() {
            return this.name + ":" + port;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof WorkerSocketAddress) {
                WorkerSocketAddress otherAddr = (WorkerSocketAddress) other;
                return name.equals(otherAddr.name);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    /** Construct client for accessing GeometryOperators server at {@code host:port}. */
    public GeometryOperatorsClient(String host, int port) {
        // using loadbalancer host and port make a request to get list of hosts and ports

//        WorkerSocketAddress workerSocketAddress = new WorkerSocketAddress(host, Integer.toString(port));
//        grpclbResolutionList = new ArrayList<ResolvedServerInfoGroup>();
//        ResolvedServerInfoGroup serverInfoGroup = ResolvedServerInfoGroup
//                .builder(Attributes
//                        .newBuilder()
//                        .set(GrpclbConstants.ATTR_LB_ADDR_AUTHORITY, host)
//                        .build())
//                .add(new ResolvedServerInfo(workerSocketAddress))
//                .build();
//
//
//        grpclbResolutionList.add(serverInfoGroup);
//
//        EquivalentAddressGroup equivalentAddressGroup = grpclbResolutionList.get(0).toEquivalentAddressGroup();
//        Attributes grpclbResolutionAttrs = Attributes.newBuilder().set(GrpclbConstants.ATTR_LB_POLICY, GrpclbConstants.LbPolicy.GRPCLB).build();
//        LoadBalancer.Subchannel subchannel = loadBalancerHelper.createSubchannel(equivalentAddressGroup, grpclbResolutionAttrs);
////        channelExecutor = new SerializingExecutor(MoreExecutors.directExecutor());
//        loadBalancer = loadBalancerFactory.newLoadBalancer(loadBalancerHelper);
//        channelExecutor.execute(new Runnable() {
//            @Override
//            public void run() {
//                loadBalancer.handleResolvedAddresses(grpclbResolutionList, grpclbResolutionAttrs);
//            }
//        });
//
////    private final ManagedChannel channel;
////    private final GeometryOperatorsBlockingStub blockingStub;
////    private final GeometryOperatorsStub asyncStub;
//        channel = subChannels.poll();
//        blockingStub = GeometryOperatorsGrpc.newBlockingStub(channel);
//        asyncStub = GeometryOperatorsGrpc.newStub(channel);
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext(true));
    }

    /** Construct client for accessing GeometryOperators server using the existing channel. */
    public GeometryOperatorsClient(ManagedChannelBuilder<?> channelBuilder) {
        channel = channelBuilder.build();
        blockingStub = GeometryOperatorsGrpc.newBlockingStub(channel);
        asyncStub = GeometryOperatorsGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void getBuffered() {
        Polyline polyline = new Polyline();
        polyline.startPath(0,0);
        polyline.lineTo(2, 3);
        polyline.lineTo(3, 3);
        // TODO inspect bug where it crosses dateline
//    polyline.startPath(-200, -90);
//    polyline.lineTo(-180, -85);
//    polyline.lineTo(-90, -70);
//    polyline.lineTo(0, 0);
//    polyline.lineTo(100, 25);
//    polyline.lineTo(170, 45);
//    polyline.lineTo(225, 64);
        OperatorExportToWkb op = OperatorExportToWkb.local();
        //TODO why does esri shape fail
        ServiceGeometry serviceGeometry = ServiceGeometry.newBuilder().setGeometryEncodingType(GeometryEncodingType.wkb).addGeometryBinary(ByteString.copyFrom(op.execute(0, polyline, null))).build();
        OperatorRequest serviceConvexOp = OperatorRequest
                .newBuilder()
                .setLeftGeometry(serviceGeometry)
                .setOperatorType(ServiceOperatorType.ConvexHull)
                .build();

        OperatorRequest serviceOp = OperatorRequest.newBuilder()
                .setLeftCursor(serviceConvexOp)
                .addBufferDistances(1)
                .setOperatorType(ServiceOperatorType.Buffer)
                .build();


        OperatorResult operatorResult = blockingStub.executeOperation(serviceOp);

        OperatorImportFromWkt op2 = OperatorImportFromWkt.local();
        Geometry result = op2.execute(0, Geometry.Type.Unknown, operatorResult.getGeometry().getGeometryString(0), null);

        boolean bContains = OperatorContains.local().execute(result, polyline, SpatialReference.create(4326), null);
    }

    /**
     * Blocking unary call example.  Calls getFeature and prints the response.
     */
    public void getFeature(int lat, int lon) {
        info("*** GetFeature: lat={0} lon={1}", lat, lon);

        ReplacePoint request = ReplacePoint.newBuilder().setLatitude(lat).setLongitude(lon).build();

        Feature feature;
        try {
            feature = blockingStub.getFeature(request);
            if (testHelper != null) {
                testHelper.onMessage(feature);
            }
        } catch (StatusRuntimeException e) {
            warning("RPC failed: {0}", e.getStatus());
            if (testHelper != null) {
                testHelper.onRpcError(e);
            }
            return;
        }
        if (GeometryOperatorsUtil.exists(feature)) {
            info("Found feature called \"{0}\" at {1}, {2}",
                    feature.getName(),
                    GeometryOperatorsUtil.getLatitude(feature.getLocation()),
                    GeometryOperatorsUtil.getLongitude(feature.getLocation()));
        } else {
            info("Found no feature at {0}, {1}",
                    GeometryOperatorsUtil.getLatitude(feature.getLocation()),
                    GeometryOperatorsUtil.getLongitude(feature.getLocation()));
        }
    }

    /**
     * Blocking server-streaming example. Calls listFeatures with a rectangle of interest. Prints each
     * response feature as it arrives.
     */
    public void listFeatures(int lowLat, int lowLon, int hiLat, int hiLon) {
        info("*** ListFeatures: lowLat={0} lowLon={1} hiLat={2} hiLon={3}", lowLat, lowLon, hiLat,
                hiLon);

        Rectangle request =
                Rectangle.newBuilder()
                        .setLo(ReplacePoint.newBuilder().setLatitude(lowLat).setLongitude(lowLon).build())
                        .setHi(ReplacePoint.newBuilder().setLatitude(hiLat).setLongitude(hiLon).build()).build();
        Iterator<Feature> features;
        try {
            features = blockingStub.listFeatures(request);
            for (int i = 1; features.hasNext(); i++) {
                Feature feature = features.next();
                info("Result #" + i + ": {0}", feature);
                if (testHelper != null) {
                    testHelper.onMessage(feature);
                }
            }
        } catch (StatusRuntimeException e) {
            warning("RPC failed: {0}", e.getStatus());
            if (testHelper != null) {
                testHelper.onRpcError(e);
            }
        }
    }

    /**
     * Async client-streaming example. Sends {@code numPoints} randomly chosen points from {@code
     * features} with a variable delay in between. Prints the statistics when they are sent from the
     * server.
     */
    public void recordRoute(List<Feature> features, int numPoints) throws InterruptedException {
        info("*** RecordRoute");
        final CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<RouteSummary> responseObserver = new StreamObserver<RouteSummary>() {
            @Override
            public void onNext(RouteSummary summary) {
                info("Finished trip with {0} points. Passed {1} features. "
                                + "Travelled {2} meters. It took {3} seconds.", summary.getPointCount(),
                        summary.getFeatureCount(), summary.getDistance(), summary.getElapsedTime());
                if (testHelper != null) {
                    testHelper.onMessage(summary);
                }
            }

            @Override
            public void onError(Throwable t) {
                warning("RecordRoute Failed: {0}", Status.fromThrowable(t));
                if (testHelper != null) {
                    testHelper.onRpcError(t);
                }
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                info("Finished RecordRoute");
                finishLatch.countDown();
            }
        };

        StreamObserver<ReplacePoint> requestObserver = asyncStub.recordRoute(responseObserver);
        try {
            // Send numPoints points randomly selected from the features list.
            for (int i = 0; i < numPoints; ++i) {
                int index = random.nextInt(features.size());
                ReplacePoint point = features.get(index).getLocation();
                info("Visiting point {0}, {1}", GeometryOperatorsUtil.getLatitude(point),
                        GeometryOperatorsUtil.getLongitude(point));
                requestObserver.onNext(point);
                // Sleep for a bit before sending the next one.
                Thread.sleep(random.nextInt(1000) + 500);
                if (finishLatch.getCount() == 0) {
                    // RPC completed or errored before we finished sending.
                    // Sending further requests won't error, but they will just be thrown away.
                    return;
                }
            }
        } catch (RuntimeException e) {
            // Cancel RPC
            requestObserver.onError(e);
            throw e;
        }
        // Mark the end of requests
        requestObserver.onCompleted();

        // Receiving happens asynchronously
        if (!finishLatch.await(1, TimeUnit.MINUTES)) {
            warning("recordRoute can not finish within 1 minutes");
        }
    }

    /**
     * Bi-directional example, which can only be asynchronous. Send some chat messages, and print any
     * chat messages that are sent from the server.
     */
    public CountDownLatch routeChat() {
        info("*** RouteChat");
        final CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<RouteNote> requestObserver =
                asyncStub.routeChat(new StreamObserver<RouteNote>() {
                    @Override
                    public void onNext(RouteNote note) {
                        info("Got message \"{0}\" at {1}, {2}", note.getMessage(), note.getLocation()
                                .getLatitude(), note.getLocation().getLongitude());
                        if (testHelper != null) {
                            testHelper.onMessage(note);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        warning("RouteChat Failed: {0}", Status.fromThrowable(t));
                        if (testHelper != null) {
                            testHelper.onRpcError(t);
                        }
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        info("Finished RouteChat");
                        finishLatch.countDown();
                    }
                });

        try {
            RouteNote[] requests =
                    {newNote("First message", 0, 0), newNote("Second message", 0, 1),
                            newNote("Third message", 1, 0), newNote("Fourth message", 1, 1)};

            for (RouteNote request : requests) {
                info("Sending message \"{0}\" at {1}, {2}", request.getMessage(), request.getLocation()
                        .getLatitude(), request.getLocation().getLongitude());
                requestObserver.onNext(request);
            }
        } catch (RuntimeException e) {
            // Cancel RPC
            requestObserver.onError(e);
            throw e;
        }
        // Mark the end of requests
        requestObserver.onCompleted();

        // return the latch while receiving happens asynchronously
        return finishLatch;
    }

    /** Issues several different requests and then exits. */
    public static void main(String[] args) throws InterruptedException {
        List<Feature> features;
        try {
            features = GeometryOperatorsUtil.parseFeatures(GeometryOperatorsUtil.getDefaultFeaturesFile());
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        GeometryOperatorsClient client = new GeometryOperatorsClient("localhost", 8980);
        try {
            // Looking for a valid feature
            client.getFeature(409146138, -746188906);

            // Feature missing.
            client.getFeature(0, 0);

            // Looking for features between 40, -75 and 42, -73.
            client.listFeatures(400000000, -750000000, 420000000, -730000000);

            // Record a few randomly selected points from the features file.
            client.recordRoute(features, 10);

            // Send and receive some notes.
            CountDownLatch finishLatch = client.routeChat();

            if (!finishLatch.await(1, TimeUnit.MINUTES)) {
                client.warning("routeChat can not finish within 1 minutes");
            }
        } finally {
            client.shutdown();
        }
    }

    private void info(String msg, Object... params) {
        logger.log(Level.INFO, msg, params);
    }

    private void warning(String msg, Object... params) {
        logger.log(Level.WARNING, msg, params);
    }

    private RouteNote newNote(String message, int lat, int lon) {
        return RouteNote.newBuilder().setMessage(message)
                .setLocation(ReplacePoint.newBuilder().setLatitude(lat).setLongitude(lon).build()).build();
    }

    /**
     * Only used for unit test, as we do not want to introduce randomness in unit test.
     */
    @VisibleForTesting
    void setRandom(Random random) {
        this.random = random;
    }

    /**
     * Only used for helping unit test.
     */
    @VisibleForTesting
    interface TestHelper {
        /**
         * Used for verify/inspect message received from server.
         */
        void onMessage(Message message);

        /**
         * Used for verify/inspect error received from server.
         */
        void onRpcError(Throwable exception);
    }

    @VisibleForTesting
    void setTestHelper(TestHelper testHelper) {
        this.testHelper = testHelper;
    }
}