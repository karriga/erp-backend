package com.jukusoft.erp.server;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import javax.transaction.xa.XAException;

public class ERPServer implements IServer {

    //vert.x options
    protected VertxOptions vertxOptions = null;

    //instance of vert.x
    protected Vertx vertx = null;

    //vert.x network server
    protected NetServer netServer = null;

    //instance of hazelcast
    protected HazelcastInstance hazelcastInstance = null;

    //vert.x cluster manager
    protected ClusterManager clusterManager = null;

    public void start() {
        //create an new hazelcast instance
        Config config = new Config();
        this.hazelcastInstance = Hazelcast.newHazelcastInstance(config);

        //http://vertx.io/docs/vertx-hazelcast/java/

        //create new vert.x cluster manager
        this.clusterManager = new HazelcastClusterManager(this.hazelcastInstance);

        //create new vertx.io options
        this.vertxOptions = new VertxOptions();

        //set cluster manager
        this.vertxOptions.setClusterManager(this.clusterManager);

        //create clustered vertx. instance
        Vertx.clusteredVertx(this.vertxOptions, res -> {
            if (res.succeeded()) {
                this.vertx = res.result();

                postStart();
            } else {
                // failed!

                System.exit(1);
            }
        });
    }

    protected void postStart () {
        //create new instance of vertx.io
        //this.vertx = Vertx.clusteredVertx(this.vertxOptions, this.vertxOptions);

        //create options for TCP network server
        NetServerOptions netServerOptions = new NetServerOptions();

        //TODO: replace this later
        int port = 2200;

        //set port
        netServerOptions.setPort(port);

        //create new instance of TCP network server
        this.netServer = this.vertx.createNetServer(netServerOptions);

        //add connection handler
        netServer.connectHandler(socket -> {
            System.out.println("new connection accepted, ip: " + socket.remoteAddress().host() + ", port: " + socket.remoteAddress().port());

            //TODO: do something with socket, for example send an message
        });

        //start network server
        this.netServer.listen(res -> {
            if (res.succeeded()) {
                System.out.println("ERP Server is now listening on port " + res.result().actualPort());
            } else {
                System.err.println("Couldnt start network server: " + res.cause());
            }
        });
    }

    public void stutdown() {
        //close network server
        netServer.close(res -> {
            if (res.succeeded()) {
                System.out.println("Server was shutdown now.");

                //close vertx.io
                vertx.close();
            } else {
                System.out.println("Server couldnt be closed.");
            }
        });
    }

}
