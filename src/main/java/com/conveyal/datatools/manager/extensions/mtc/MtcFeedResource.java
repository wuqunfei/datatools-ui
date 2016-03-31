package com.conveyal.datatools.manager.extensions.mtc;

import com.conveyal.datatools.manager.DataManager;
import com.conveyal.datatools.manager.extensions.ExternalFeedResource;
import com.conveyal.datatools.manager.models.ExternalFeedSourceProperty;
import com.conveyal.datatools.manager.models.FeedSource;
import com.conveyal.datatools.manager.models.FeedVersion;
import com.conveyal.datatools.manager.models.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by demory on 3/30/16.
 */
public class MtcFeedResource implements ExternalFeedResource {

    public static final Logger LOG = LoggerFactory.getLogger(MtcFeedResource.class);

    private String rtdApi;

    public MtcFeedResource() {
        rtdApi = DataManager.config.getProperty("application.extensions.mtc.rtd_api");
    }

    @Override
    public String getResourceType() {
        return "MTC";
    }

    @Override
    public void importFeedsForProject(Project project) {
        URL url;
        ObjectMapper mapper = new ObjectMapper();
        // single list from MTC
        try {
            url = new URL(rtdApi + "/Carrier");
        } catch(MalformedURLException ex) {
            LOG.error("Could not construct URL for RTD API: " + rtdApi);
            return;
        }

        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            // optional default is GET
            con.setRequestMethod("GET");

            //add request header
            con.setRequestProperty("User-Agent", "User-Agent");

            int responseCode = con.getResponseCode();
            System.out.println("\nSending 'GET' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            String json = response.toString();
            System.out.println(json);
            RtdCarrier[] results = mapper.readValue(json, RtdCarrier[].class);
            for (int i = 0; i < results.length; i++) {
                //                    String className = "RtdCarrier";
                //                    Object car = Class.forName(className).newInstance();
                RtdCarrier car = results[i];
                System.out.println("car id=" + car.AgencyId + " name=" + car.AgencyName);

                FeedSource source = null;

                // check if a FeedSource with this AgencyId already exists
                for (FeedSource existingSource : project.getProjectFeedSources()) {
                    ExternalFeedSourceProperty agencyIdProp =
                            ExternalFeedSourceProperty.find(existingSource, this.getResourceType(), "AgencyId");
                    if (agencyIdProp != null && agencyIdProp.value.equals(car.AgencyId)) {
                        System.out.println("already exists: " + car.AgencyId);
                        source = existingSource;
                    }
                }

                String feedName;
                if (car.AgencyName != null) {
                    feedName = car.AgencyName;
                } else if (car.AgencyShortName != null) {
                    feedName = car.AgencyShortName;
                } else {
                    feedName = car.AgencyId;
                }

                if (source == null) source = new FeedSource(feedName);
                else source.name = feedName;

                source.setProject(project);

                source.save();

                // create / update the properties

                for(Field carrierField : car.getClass().getDeclaredFields()) {
                    String fieldName = carrierField.getName();
                    String fieldValue = carrierField.get(car) != null ? carrierField.get(car).toString() : null;

                    ExternalFeedSourceProperty.updateOrCreate(source, this.getResourceType(), fieldName, fieldValue);
                }
            }
        } catch(Exception ex) {
            LOG.error("Could not read feeds from MTC RTD API");
            ex.printStackTrace();
        }
    }

    @Override
    public void propertyUpdated(ExternalFeedSourceProperty property) {
        LOG.info("Update property in MTC carrier table: " + property.name);
    }

    @Override
    public void feedVersionUpdated(FeedVersion feedVersion) {
        LOG.info("Pushing to MTC S3 Bucket");
    }
}