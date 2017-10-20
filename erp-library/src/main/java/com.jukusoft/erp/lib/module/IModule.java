package com.jukusoft.erp.lib.module;

import com.jukusoft.erp.lib.context.AppContext;
import com.jukusoft.erp.lib.logging.ILogging;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public interface IModule {

    /**
     * get a reference to the Vert.x instance that deployed this verticle
     *
     * @return reference to the Vert.x instance
     */
    public Vertx getVertx ();

    /**
    * get instance of logger
    */
    public ILogging getLogger ();

    /**
    * initialize module
    */
    public void init (Vertx vertx, AppContext context, ILogging logger);

    /**
    * start module
    */
    public void start (Future<Void> startFuture) throws Exception;

    /**
    * stop module
    */
    public void stop (Future<Void> stopFuture) throws Exception;

}
