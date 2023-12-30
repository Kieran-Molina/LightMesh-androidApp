package com.kieran.lightmesh;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.skydoves.colorpickerview.ColorPickerDialog;
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

	private TextView gfgTextView;

	private Button mSaveColorButton, mDelColorButton, mLoadColorButton, mSetModeButton;
	private ImageButton mSaveUrlButton, mToggleOnOffButton;
	private View mColorMainPreview, mColorSecPreview;
	private EditText mTextUrl, mTextColorMain, mTextColorSec;
	private Spinner mSpinnerMode, mSpinnerColor;
	private CheckBox mCb1, mCb2, mCb3, mCb4, mCb5;
	private SharedPreferences.Editor mEditor;
	private int[] mColor;
	private boolean currentState = false;
	private String mUrl;

	private JSONObject jObjSavedColors;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		gfgTextView = findViewById(R.id.gfg_heading);
		mSaveColorButton = findViewById(R.id.save_color_button);
		mDelColorButton = findViewById(R.id.del_color_button);
		mLoadColorButton = findViewById(R.id.load_color_button);
		mSetModeButton = findViewById(R.id.buttonMode);
		mSaveUrlButton = findViewById(R.id.saveUrlButton);
		mToggleOnOffButton = findViewById(R.id.buttonOnOff);
		mColorMainPreview = findViewById(R.id.preview_main_color);
		mColorSecPreview = findViewById(R.id.preview_sec_color);
		mTextUrl = findViewById(R.id.editTextUrl);
		mTextColorMain = findViewById(R.id.editTextColorMain);
		mTextColorSec = findViewById(R.id.editTextColorSec);
		mSpinnerMode = findViewById(R.id.spinnerMode);
		mSpinnerColor = findViewById(R.id.spinnerColor);

		mCb1 = findViewById(R.id.checkBox1);
		mCb2 = findViewById(R.id.checkBox2);
		mCb3 = findViewById(R.id.checkBox3);
		mCb4 = findViewById(R.id.checkBox4);
		mCb5 = findViewById(R.id.checkBox5);

		mColor = new int[2];
		String[] modes = {"Off", "Static", "Snake", "Rainbow"};

		SharedPreferences mPrefs = getSharedPreferences("lightMesh", 0);
		mEditor = mPrefs.edit();

		ArrayAdapter<String> adaptMode = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
		adaptMode.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpinnerMode.setAdapter(adaptMode);
		mSpinnerMode.setSelection(2);
		initSavedColor(mPrefs);

		mColor[0] = mPrefs.getInt("color0", 0xFF1F4A90);
		mColor[1] = mPrefs.getInt("color1", 0xFF90651F);
		mUrl = mPrefs.getString("url", "http://192.168.1.30:5005");
		mColorMainPreview.setBackgroundColor(mColor[0]);
		mColorSecPreview.setBackgroundColor(mColor[1]);
		mTextUrl.setText(mUrl);
		mTextColorMain.setText(Integer.toHexString(mColor[0]).substring(2).toUpperCase());
		mTextColorSec.setText(Integer.toHexString(mColor[1]).substring(2).toUpperCase());

		mColorMainPreview.setOnClickListener(
				v -> openColorSelect(0, mColorMainPreview, mTextColorMain));
		mColorSecPreview.setOnClickListener(
				v -> openColorSelect(1, mColorSecPreview, mTextColorSec));

		mTextColorMain.setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) {
				textColorSelect(0, mColorMainPreview, mTextColorMain);
			}
		});
		mTextColorSec.setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) {
				textColorSelect(1, mColorSecPreview, mTextColorSec);
			}
		});
		gfgTextView.setOnClickListener(v -> displayLastError(mPrefs));

		mSaveColorButton.setOnClickListener(v -> {
			saveColor(mPrefs);
		});

		mLoadColorButton.setOnClickListener(v -> {
			getSavedColor();
		});

		mDelColorButton.setOnClickListener(v -> {
			delSavedColor(mPrefs);
		});

		mSetModeButton.setOnClickListener(v -> {
			sendPost(getCurrentJSONParams(), true);
		});

		mToggleOnOffButton.setOnClickListener(v -> {
			JSONObject jobj = new JSONObject();
			try {
				jobj.put("onoff", !currentState);
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			sendPost(jobj, true);
		});

		mSaveUrlButton.setOnClickListener(v -> {
			mUrl = mTextUrl.getText().toString();
			mEditor.putString("url", mUrl).commit();
			Toast.makeText(getApplicationContext(), "Saved url : " + mUrl,
					Toast.LENGTH_SHORT).show();
		});
		sendPost(new JSONObject(), true);
	}

	private void initSavedColor(SharedPreferences prefs) {
		String saved = prefs.getString("savedColorsJson", "{}");
		try {
			jObjSavedColors = new JSONObject(saved);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		ArrayList<String> colorNames = new ArrayList<>();
		jObjSavedColors.keys().forEachRemaining(colorNames::add);
		ArrayAdapter<String> adaptColor = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, colorNames);
		adaptColor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mSpinnerColor.setAdapter(adaptColor);
	}

	private void saveColor(SharedPreferences prefs) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Color preset name");

		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_TEXT);
		builder.setView(input);

		builder.setPositiveButton("Save", (dialog, which) -> {
			String name = input.getText().toString();
			JSONObject obj = getCurrentJSONColor();
			try {
				jObjSavedColors.put(name, obj);
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			mEditor.putString("savedColorsJson", jObjSavedColors.toString()).commit();
			initSavedColor(prefs);
		});
		builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

		builder.show();
	}

	private void getSavedColor() {
		String name = (String) mSpinnerColor.getSelectedItem();
		try {
			JSONObject obj = jObjSavedColors.getJSONObject(name);
			mColor[0] = obj.getInt("0") | 0xFF000000;
			mColor[1] = obj.getInt("1") | 0xFF000000;
			mColorMainPreview.setBackgroundColor(mColor[0]);
			mColorSecPreview.setBackgroundColor(mColor[1]);
			mTextColorMain.setText(Integer.toHexString(mColor[0]).substring(2).toUpperCase());
			mTextColorSec.setText(Integer.toHexString(mColor[1]).substring(2).toUpperCase());
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	private void delSavedColor(SharedPreferences prefs) {
		String name = (String) mSpinnerColor.getSelectedItem();
		jObjSavedColors.remove(name);
		mEditor.putString("savedColorsJson", jObjSavedColors.toString()).commit();
		initSavedColor(prefs);
	}

	private void openColorSelect(int colorIndex, View preview, EditText text) {
		new ColorPickerDialog.Builder(this)
				.setTitle("Color " + (colorIndex + 1))
				.setPreferenceName("LightMeshColor" + colorIndex)
				.setPositiveButton(getString(R.string.confirm),
						(ColorEnvelopeListener) (envelope, fromUser) -> {
							setInternalColor(colorIndex, envelope.getColor(), preview, text);
						})
				.setNegativeButton(getString(R.string.cancel),
						(dialogInterface, i) -> dialogInterface.dismiss())
				.attachAlphaSlideBar(false)
				.attachBrightnessSlideBar(true)
				.setBottomSpace(12)
				.show();
	}

	private void textColorSelect(int colorIndex, View preview, EditText text) {
		String str = text.getText().toString();
		if (str.length() == 6) {
			try {
				int color = Integer.parseInt(str, 16) | 0xFF000000; // fill alpha
				setInternalColor(colorIndex, color, preview, text);
			} catch (NumberFormatException e) {
				text.setText(Integer.toHexString(mColor[colorIndex]).substring(2).toUpperCase());
			}
		}
	}

	private void setInternalColor(int colorIndex, int color, View preview, EditText text) {
		mColor[colorIndex] = color;
		preview.setBackgroundColor(mColor[colorIndex]);
		text.setText(Integer.toHexString(mColor[colorIndex]).substring(2).toUpperCase());
		mEditor.putInt("color" + colorIndex, mColor[colorIndex]).commit();

	}

	private int getZoneSelection() {
		int sel = 0;
		sel += mCb1.isChecked() ? 0b00001 : 0;
		sel += mCb2.isChecked() ? 0b00010 : 0;
		sel += mCb3.isChecked() ? 0b00100 : 0;
		sel += mCb4.isChecked() ? 0b01000 : 0;
		sel += mCb5.isChecked() ? 0b10000 : 0;
		return sel;
	}

	private JSONObject getCurrentJSONParams() {
		JSONObject jobj = new JSONObject();
		try {
			jobj.put("zone", getZoneSelection());
			jobj.put("effect", mSpinnerMode.getSelectedItemPosition());
			jobj.put("color", getCurrentJSONColor());
			jobj.put("onoff", true);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		return jobj;
	}

	private JSONObject getCurrentJSONColor() {
		JSONObject jobjColor = new JSONObject();
		try {
			jobjColor.put("0", mColor[0] & 0x00FFFFFF);
			jobjColor.put("1", mColor[1] & 0x00FFFFFF);
			jobjColor.put("2", mColor[1] & 0x00FFFFFF);
			jobjColor.put("3", mColor[1] & 0x00FFFFFF);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		return jobjColor;
	}

	public void displayCurrentState(boolean cs) {
		runOnUiThread(() -> {
			currentState = cs;
			gfgTextView.setText("LightMesh " + (currentState ? "ON" : "OFF"));
		});

	}

	public void displayLastError(SharedPreferences prefs) {
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		builder.setTitle("Last error - " + prefs.getString("errorDate", "none"));
		builder.setMessage(prefs.getString("errorText", "-"));
		builder.setCancelable(true);
		builder.show();
	}

	public void sendPost(JSONObject jsonToSend, boolean showToast) {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					URL url = new URL(mUrl + "/lightsend");
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("POST");
					conn.setConnectTimeout(3000);
					conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
					conn.setRequestProperty("Accept", "application/json");
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
						displayCurrentState(cs);
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
				} catch (java.net.SocketTimeoutException e) {
					logMessage("Timeout", showToast);
				} catch (JSONException | IOException e) {
					logMessage("error : " + e.getMessage(), showToast);
				}
			}
		});

		thread.start();
	}

	private void logMessage(String msg, boolean showToast) {
		Log.d("LightMesh", msg);

		if (showToast) {
			runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
		}
		mEditor.putString("errorDate", String.valueOf(new Date()));
		mEditor.putString("errorText", msg);
		mEditor.commit();
	}
}

