package com.jukusoft.data.repository;

import com.jukusoft.data.entity.Group;
import com.jukusoft.data.entity.GroupMember;
import com.jukusoft.erp.lib.cache.CacheTypes;
import com.jukusoft.erp.lib.cache.ICache;
import com.jukusoft.erp.lib.cache.InjectCache;
import com.jukusoft.erp.lib.database.AbstractMySQLRepository;
import com.jukusoft.erp.lib.database.InjectRepository;
import com.jukusoft.erp.lib.permission.PermissionStates;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.CompositeFutureImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermissionRepository extends AbstractMySQLRepository {

    @InjectCache(name = "group-permissions-cache", type = CacheTypes.HAZELCAST_CACHE)
    protected ICache groupPermCache;

    @InjectRepository
    protected GroupRepository groupRepository;

    public void listPermissionsByGroup (long groupID, Handler<AsyncResult<Map<String,PermissionStates>>> handler) {
        if (groupID <= 0) {
            throw new IllegalArgumentException("groupID cannot be <= 0.");
        }

        if (this.groupPermCache == null) {
            throw new NullPointerException("group permission cache cannot be null.");
        }

        //check, if result is already in cache
        if (this.groupPermCache.contains("group-permissions-" + groupID)) {
            //get cache object
            JsonArray jsonArray = this.groupPermCache.getArray("group-permissions-" + groupID);

            //convert rows to list
            Map<String,PermissionStates> map = this.createMapFromJSONArray(jsonArray);

            handler.handle(Future.succeededFuture(map));

            return;
        }

        //
    }

    protected Map<String,PermissionStates> createMapFromJSONArray (JsonArray array) {
        Map<String,PermissionStates> map = new HashMap<>();

        return map;
    }

    public void listPermissionsByUser (long userID, Handler<AsyncResult<Map<String,PermissionStates>>> handler) {
        //first, get groups of user
        this.groupRepository.listGroupIDsOfUser(userID, res -> {
            if (!res.succeeded()) {
                handler.handle(Future.failedFuture(res.cause()));
                return;
            }

            //get array with groupIDs (member groups from user)
            long[] groupIDs = res.result();

            //create new hashmap for permissions
            Map<String,PermissionStates> permMap = new HashMap<>();

            List<Future<Map<String,PermissionStates>>> futureList = new ArrayList<>();

            for (long groupID : groupIDs) {
                Future<Map<String,PermissionStates>> permFuture = Future.future();
                this.listPermissionsByGroup(groupID, permFuture);

                futureList.add(permFuture);
            }

            //wait until all futures are succeeded or one of them failed
            CompositeFutureImpl.all((Future<?>) futureList).setHandler(res1 -> {
                if (!res1.succeeded()) {
                    handler.handle(Future.failedFuture(res1.cause()));
                    return;
                }

                //merge groups
                for (Future<Map<String,PermissionStates>> future : futureList) {
                    Map<String,PermissionStates> permMap1 = future.result();
                    mergePermissions(permMap1, permMap);
                }

                handler.handle(Future.succeededFuture(permMap));
            });
        });
    }

    private void mergePermissions (Map<String,PermissionStates> permMap, Map<String,PermissionStates> resultMap) {
        for (Map.Entry<String,PermissionStates> entry : permMap.entrySet()) {
            String permission = entry.getKey();
            PermissionStates value = entry.getValue();

            if (resultMap.containsKey(permission)) {
                //we have to merge values

                PermissionStates oldValue = resultMap.get(permission);

                if (oldValue == PermissionStates.NEVER) {
                    //we dont have to override anyting
                    continue;
                }

                if (value == PermissionStates.NEVER) {
                    //always override
                    resultMap.put(permission, value);

                    continue;
                }

                if (oldValue == PermissionStates.DISALLOW) {
                    //override value
                    resultMap.put(permission, value);
                } else if (oldValue == PermissionStates.ALLOW) {
                    //we dont have to do anything
                }
            } else {
                //set new value
                resultMap.put(permission, value);
            }
        }
    }

}
