package com.jukusoft.erp.lib.session.impl;

import com.jukusoft.erp.lib.json.JSONLoadable;
import com.jukusoft.erp.lib.json.JSONSerializable;
import com.jukusoft.erp.lib.session.ChangeableSessionManager;
import com.jukusoft.erp.lib.session.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Session implements JSONSerializable, JSONLoadable {

    //unique id of session
    protected final String sessionID;

    //session attributes
    protected Map<String,Object> attributes = new HashMap<>();

    //created unix timestamp
    protected long created = 0;

    private ChangeableSessionManager sessionManager = null;

    //flag, if user is logged in
    protected boolean isLoggedIn = false;

    //userID of -1, if user isnt logged in
    protected long userID = -1;

    /**
    * default constructor
     *
     * @param sessionID unique session id
    */
    public Session (String sessionID) {
        this.sessionID = sessionID;

        this.created = System.currentTimeMillis();
    }

    /**
    * get unique ID of session
     *
     * @return unique session id
    */
    public String getSessionID () {
        return this.sessionID;
    }

    /**
    * set a session attribute
     *
     * @param key key
     * @param value value
    */
    public <T> void putAttribute (String key, T value) {
        this.attributes.put(key, value);
    }

    /**
    * remove attribute by key
     *
     * @param key key of attribute
    */
    public void removeAttribute (String key) {
        this.attributes.remove(key);
    }

    /**
    * get attribute
    */
    public <T> T getAttribute (String key, Class<T> expectedClass) {
        if (!this.attributes.containsKey(key)) {
            return null;
        }

        return expectedClass.cast(this.attributes.get(key));
    }

    public boolean containsAttribute (String key) {
        return this.attributes.containsKey(key);
    }

    /**
    * writes session attributes to cache
    */
    public void flush () {
        this.sessionManager.putSession(this.sessionID, this);
    }

    public boolean isLoggedIn () {
        return this.isLoggedIn;
    }

    @Override
    public JSONObject toJSON() {
        //create new json object
        JSONObject json = new JSONObject();

        //add session ID
        json.put("session-id", this.sessionID);

        //add created timestamp
        json.put("created", this.created);

        //add meta information
        JSONArray jsonArray = new JSONArray();

        for (Map.Entry<String,Object> entry : attributes.entrySet()) {
            JSONObject json1 = new JSONObject();
            json1.put("key", entry.getKey());
            json1.put("value", entry.getValue());
        }

        json.put("meta", jsonArray);

        return json;
    }

    @Override
    public void loadFromJSON(JSONObject json) {
        //this.sessionID = json.getString("session-id");

        this.created = json.getLong("created");

        JSONArray jsonArray = json.getJSONArray("meta");

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject json1 = jsonArray.getJSONObject(i);

            String key = json1.getString("key");
            String value = json1.getString("value");
        }
    }

    public static Session createFromJSON (JSONObject json, ChangeableSessionManager sessionManager) {
        //create new session with session id
        Session session = new Session(json.getString("session-id"));

        //load meta information
        session.loadFromJSON(json);

        session.sessionManager = sessionManager;

        return session;
    }

}
