package org.wikipedia.analytics;

import org.json.JSONException;
import org.json.JSONObject;
import org.wikipedia.Site;
import org.wikipedia.WikipediaApp;

public class ToCInteractionFunnel extends Funnel {
    private static final String SCHEMA_NAME = "MobileWikiAppToCInteraction";
    private static final int REV_ID = 10375484;

    private final String appInstallID;
    private final Site site;

    public ToCInteractionFunnel(WikipediaApp app, Site site) {
        super(app, SCHEMA_NAME, REV_ID);

        //Retrieve this app installation's unique ID, used to record unique users of features
        appInstallID = app.getAppInstallID();

        this.site = site;
    }

    @Override
    protected JSONObject preprocessData(JSONObject eventData) {
        try {
            eventData.put("appInstallID", appInstallID);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return eventData;
    }

    protected void log(Object... params) { super.log(site, params); }

    public void logOpen() {
        log(
                "action", "open"
        );
    }

    public void logClose() {
        log(
                "action", "close"
        );
    }

    public void logClick() {
        log(
                "action", "click"
        );
    }
}
