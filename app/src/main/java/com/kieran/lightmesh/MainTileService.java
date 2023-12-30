package com.kieran.lightmesh;

import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

public class MainTileService extends TileService {

    private boolean currentState = false;
    private String mUrl;
    private SharedPreferences.Editor mEditor;


    // Called when the user adds your tile.
    @Override
    public void onTileAdded() {
        super.onTileAdded();
        getQsTile().setState(Tile.STATE_INACTIVE);
    }

    // Called when your app can update your tile.
    @Override
    public void onStartListening() {
        super.onStartListening();
        SharedPreferences mPrefs = getSharedPreferences("lightMesh", 0);
        mEditor = mPrefs.edit();
        mUrl = mPrefs.getString("url", "");
        sendPostTile(new JSONObject(), false);
    }

    // Called when your app can no longer update your tile.
    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    // Called when the user taps on your tile in an active or inactive state.
    @Override
    public void onClick() {
        super.onClick();
        JSONObject jobj = new JSONObject();
        try {
            jobj.put("onoff", !currentState);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        sendPostTile(jobj, false);
    }

    // Called when the user removes your tile.
    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

    public void sendPostTile(JSONObject jsonToSend, boolean showToast) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(mUrl + "/lightsend");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(3000);
                    conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                    conn.setRequestProperty("Accept","application/json");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    JSONObject jsonParam = new JSONObject();
                    jsonParam.put("send", jsonToSend);
                    os.writeBytes(jsonParam.toString());

                    os.flush();
                    os.close();

                    if (conn.getResponseCode() == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                        String msg = br.readLine();
                        Log.d("MSG", msg);
                        br.close();
                        JSONObject jobj = new JSONObject(msg);
                        JSONObject jmsg = jobj.getJSONObject("message");
                        boolean cs = jmsg.getBoolean("onoff");
                        setCurrentState(cs);
                    } else if (conn.getResponseCode() == 500) {
                        logMessage("Error 500 from server", showToast);
                    } else {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                        String msg = br.readLine();
                        Log.d("MSG", msg);
                        br.close();
                        JSONObject jobj = new JSONObject(msg);
                        logMessage(jobj.getString("message") + " - " + conn.getResponseCode(), showToast);
                    }

                    conn.disconnect();
                }  catch (java.net.SocketTimeoutException e) {
                    logMessage("Timeout - If the server is fine, check android settings - battery - app usage restrictions, it should be unrestricted", showToast);
                } catch (JSONException | IOException e) {
                    logMessage("error : " + e.getMessage(), showToast);
                }
            }
        });

        thread.start();
    }

    public void setCurrentState(boolean cs) {
        currentState = cs;
        Tile tile = getQsTile();
        tile.setState(cs ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    private void logMessage(String msg, boolean showToast) {
        Log.d("LightMesh", msg);

        if (showToast) {
            //runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }
        mEditor.putString("errorDate", String.valueOf(new Date()));
        mEditor.putString("errorText", msg);
        mEditor.commit();
    }
}