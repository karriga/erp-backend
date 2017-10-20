package com.jukusoft.erp.server;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IdGenerator;
import com.jukusoft.erp.lib.gateway.ApiGateway;
import com.jukusoft.erp.lib.gateway.ResponseHandler;
import com.jukusoft.erp.lib.logging.ILogging;
import com.jukusoft.erp.lib.message.ResponseType;
import com.jukusoft.erp.lib.message.request.ApiRequest;
import com.jukusoft.erp.lib.message.response.ApiResponse;
import com.jukusoft.erp.server.gateway.DefaultApiGateway;
import com.jukusoft.erp.server.logger.HzLogger;
import com.jukusoft.erp.server.message.ResponseGenerator;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.json.JSONObject;

import javax.transaction.xa.XAException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

    //api gateway
    protected ApiGateway gateway = null;

    protected ILogging logger = null;

    //hazelcast ID generator for cluster-wide unique IDs
    protected IdGenerator idGenerator = null;

    public void start() {
        //create an new hazelcast instance
        Config config = new Config();
        this.hazelcastInstance = Hazelcast.newHazelcastInstance(config);

        //http://vertx.io/docs/vertx-hazelcast/java/

        this.idGenerator = this.hazelcastInstance.getIdGenerator("message-id-generator");

        //create logger
        this.logger = new HzLogger(this.hazelcastInstance);

        //create new vert.x cluster manager
        this.clusterManager = new HazelcastClusterManager(this.hazelcastInstance);

        //create new vertx.io options
        this.vertxOptions = new VertxOptions();

        //use clustered mode of vert.x
        this.vertxOptions.setClustered(true);

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
        //create api gateway
        this.gateway = new DefaultApiGateway(this.vertx, this.logger);

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

            logger.debug("new_tcp_connection", "ip: " + socket.remoteAddress().host() + ", port: " + socket.remoteAddress().port());

            //set close handler
            socket.closeHandler(v -> {
                logger.debug("close_tcp_connection", "ip: " + socket.remoteAddress().host() + ", port: " + socket.remoteAddress().port());
            });

            //set message handler
            socket.handler(buffer -> {
                //convert to string and json object
                String str = buffer.toString(StandardCharsets.UTF_8);

                JSONObject json = new JSONObject(str);

                //generate cluster-wide unique message id
                final long messageID = generateMessageID();

                //remove whitespaces at begin and end
                str = str.trim();

                //check, if message is an json message
                if (!str.startsWith("{") || !str.endsWith("}")) {
                    //no json message
                    logger.warn(messageID, "bad_request", "invalide json message: " + str);

                    //generate response string
                    String str1 = ResponseGenerator.generateResponse("error", ResponseType.BAD_REQUEST);

                    //write to the response and end it
                    socket.write(str1);

                    return;
                }

                //check, if event name exists
                if (!json.has("event")) {
                    logger.warn(messageID, "bad_request", "event doesnt exists in message: " + json.toString());

                    //generate response string
                    String str1 = ResponseGenerator.generateResponse("error", ResponseType.BAD_REQUEST);

                    //write to the response and end it
                    socket.write(str1);

                    return;
                }

                //get event name
                String event = json.getString("event");

                //get data
                JSONObject data = json.getJSONObject("data");

                //create api request
                ApiRequest req = new ApiRequest(event, data, messageID);

                //log request
                this.logger.debug(messageID, "new_tcp_request", req.toString());

                this.gateway.handleRequestAsync(req, new ResponseHandler() {
                    @Override
                    public void handleResponse(ApiResponse res) {
                        //send response
                        String str = ResponseGenerator.generateResponse(res.getEvent(), res.getData(), res.getStatusCode());

                        //write to the response and end it
                        socket.write(str);

                        logger.debug(messageID, "request_succedded", res.toString());
                    }

                    @Override
                    public void responseFailed() {
                        //generate response string
                        String str = ResponseGenerator.generateResponse(event, ResponseType.SERVICE_UUNAVAILABLE);

                        //write to the response and end it
                        socket.write(str);

                        logger.warn(messageID, "request_failed", req.toString() + ", cause: " + ResponseType.SERVICE_UUNAVAILABLE.name() + ".");
                    }
                });
            });
        });

        //start network server
        this.netServer.listen(res -> {
            if (res.succeeded()) {
                System.out.println("ERP Server is now listening on port " + res.result().actualPort());
            } else {
                System.err.println("Couldnt start network server: " + res.cause());

                System.exit(1);
            }
        });

        //start http server (for debugging and rest api)
        this.startHTTPServer(8080);
    }

    public void startHTTPServer (int port) {
        HttpServerOptions options = new HttpServerOptions();
        options.setPort(port);

        //create new http server
        HttpServer server = vertx.createHttpServer(options);

        server.requestHandler(request -> {

            // This handler gets called for each request that arrives on the server
            HttpServerResponse response = request.response();
            response.putHeader("content-type", "application/json");

            //get event name
            String event = request.path();

            JSONObject data = new JSONObject();

            if (request.method() == HttpMethod.POST) {
                if (request.formAttributes().contains("event")) {
                    event = request.formAttributes().get("event");
                }

                //converts all form attributes to json object
                for (Map.Entry<String,String> entry : request.formAttributes().entries()) {
                    data.put(entry.getKey(), entry.getValue());
                }
            }

            final String eventName = event;

            //generate cluster-wide unique message id
            final long messageID = generateMessageID();

            //create api request
            ApiRequest req = new ApiRequest(event, data, messageID);

            //log request
            this.logger.debug(messageID, "new_http_request", req.toString());

            this.gateway.handleRequestAsync(req, new ResponseHandler() {
                @Override
                public void handleResponse(ApiResponse res) {
                    //send response
                    String str = ResponseGenerator.generateResponse(res.getEvent(), res.getData(), res.getStatusCode());

                    //write to the response and end it
                    response.end(str);

                    logger.debug(messageID, "request_succedded", res.toString());
                }

                @Override
                public void responseFailed() {
                    //generate response string
                    String str = ResponseGenerator.generateResponse(eventName, ResponseType.SERVICE_UUNAVAILABLE);

                    //write to the response and end it
                    response.end(str);

                    logger.warn(messageID, "request_failed", req.toString() + ", cause: " + ResponseType.SERVICE_UUNAVAILABLE.name() + ".");
                }
            });
        });

        //start http server
        server.listen(res -> {
            if (res.succeeded()) {
                System.out.println("HTTP Server is now listening on port " + res.result().actualPort());
            } else {
                System.err.println("Couldnt start HTTP server: " + res.cause());

                System.exit(1);
            }
        });
    }

    protected long generateMessageID () {
        return this.idGenerator.newId();
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
