package com.reddragon.pmtest;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Created by frysingg on 4/28/2017.
 */

class JSONDownloader {
    private static final String TAG = "JSONDownloader";

    byte[] getUrlBytes(String urlSpec) throws IOException {
        URL url = new URL(urlSpec);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)url.openConnection();
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            InputStream in = connection.getInputStream();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                // Whoops - what happened?
                throw new IOException(connection.getResponseMessage() +
                        ": with " +
                        urlSpec);
            }
            int bytesRead = 0;
            byte[] buffer = new byte[1024];
            while ((bytesRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }
            out.close();
            return out.toByteArray();
        } finally {
            if ( connection != null ) {
                connection.disconnect();
            }
        }
    }
    private String getUrlString(String urlSpec) throws IOException {
        return new String(getUrlBytes(urlSpec));
    }

    List<ContentItem> retrieveItems(String url) {

        List<ContentItem> items = null;
        JSONArray jsonBody = null;

        try {

            String jsonString = getUrlString(url);

            // Standard parsing approach
            //items = new ArrayList<>();
            //jsonBody = new JSONArray(jsonString);
            //parseItems(items, jsonBody);

            // Alternative method - use GSON
            Gson gson = new GsonBuilder().create();
            Type listType = new TypeToken<List<ContentItem>>(){}.getType();
            items = (List<ContentItem>) gson.fromJson( jsonString, listType);

        } catch (IOException ioe) {
            Log.e(TAG, "Failed to fetch items", ioe);
        } catch (Exception e) {
            Log.e(TAG, "General error parsing JSON: " + e.getMessage(), e);
        }

        return items;
    }

    /**
     * Alternative way to directly parse without GSON involved...
     */
    private void parseItems(List<ContentItem> items, JSONArray jsonArray)
            throws IOException, JSONException {


        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject imageJsonObject = jsonArray.getJSONObject(i);

            ContentItem item = new ContentItem();
            if (imageJsonObject.has("title")) {
                item.setTitle(imageJsonObject.getString("title"));
            }
            if (imageJsonObject.has("author")) {
                item.setAuthor(imageJsonObject.getString("author"));
            }
            if (imageJsonObject.has("imageURL")) {
                item.setImageUrl(imageJsonObject.getString("imageURL"));
            }

            items.add(item);
        }
    }



}
