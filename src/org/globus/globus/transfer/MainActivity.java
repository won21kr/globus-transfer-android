/*
 * Copyright 2012 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.globus.globus.transfer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONObject;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;

public class MainActivity extends Activity {
	
	private TextView loginNotice;
	private Context context;
	private EditText usernameField;
	private EditText passwordField;
	private String username = null;
	private String samlCookie = null;
	private ProgressBar loginProgress;
	
	
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        loginNotice = (TextView) findViewById(R.id.login_notice);
        
        loginProgress = (ProgressBar) findViewById(R.id.login_progress_bar);
        loginProgress.setMax(7);
        
        context = this;
    }
    
    
    @Override
    public void onConfigurationChanged(Configuration  newConfig) 
    {
      super.onConfigurationChanged(newConfig);
    }
    
    
    public void doGOUserLogin(View v) 
    {
    	usernameField = (EditText) findViewById(R.id.login_username);
    	String usernameStr = usernameField.getText().toString();
    	
    	passwordField = (EditText) findViewById(R.id.login_password);
    	String passwordStr = passwordField.getText().toString();
    	
    	new GOUserLogInAttempt().execute(usernameStr, passwordStr);
    }
    
    private class GOUserLogInAttempt extends AsyncTask<String, Void, String> 
    {
    	
    	@Override
    	protected void onPreExecute() 
    	{
    		//the color is set to blue to indicate that there is some sort of action going on
        	loginNotice.setTextColor(context.getResources().getColor(R.color.attempt_color));
        	loginNotice.setText(context.getResources().getString(R.string.login_attempting_login));
        	loginProgress.setVisibility(View.VISIBLE);
    	}
    	
    	
		@Override
		protected String doInBackground(String... credentials) 
		{
			String usernameStr = credentials[0];
			String passwordStr = credentials[1];
			
			if(usernameStr.equals("") && passwordStr.equals(""))
	    		return context.getResources().getString(R.string.login_notice_no_username_and_password);
	    	if(usernameStr.equals(""))
	    		return context.getResources().getString(R.string.login_notice_no_username);
	    	if(passwordStr.equals(""))
	    		return context.getResources().getString(R.string.login_notice_no_password);
			
	    	loginProgress.setProgress(1);
	    	
			HttpsURLConnection urlConnection = null;
			JSONObject content = new JSONObject();
			String bodyContentStr = "";
			JSONObject bodyContent = null;
			
			try {
				content.put("username", usernameStr);
				content.put("password", passwordStr);
				
				URL url = new URL("https://www.globusonline.org/service/graph/authenticate");
				
				urlConnection = (HttpsURLConnection) url.openConnection();
				
				loginProgress.setProgress(2);
				
				urlConnection.setRequestMethod("POST");
				urlConnection.setRequestProperty("Content-Type", "application/json");
				urlConnection.setRequestProperty("Content-Length", "" + content.toString().getBytes().length);
				urlConnection.setRequestProperty("Content-Language", "en-US");
				urlConnection.setDoOutput(true);
				urlConnection.setDoInput(true);
				
				urlConnection.connect();
				
				loginProgress.setProgress(3);
				
				//return "No Connectivity Message" there is no network access
				if(!isNetworkAvailable())
					return context.getResources().getString(R.string.no_connectivity);
				
				DataOutputStream out = new DataOutputStream(urlConnection.getOutputStream());
				
				out.writeBytes(content.toString());
				out.flush();
				out.close();
				
				loginProgress.setProgress(4);
				
				BufferedReader in = null;
				
				try {
					
					in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
					String line = "";
					while((line=in.readLine())!=null) {
						bodyContentStr += line;
					}
					in.close();
					bodyContent = new JSONObject(bodyContentStr);
					
					
				} catch (Exception e) {  //the api returned a failed authentication
					return context.getResources().getString(R.string.login_notice_failure);
				}
				
				loginProgress.setProgress(5);
				
				Map<String, List<String>> allHeaderFields = urlConnection.getHeaderFields();
				List<String> allCookieFields = allHeaderFields.get("Set-Cookie");
				
				for(int i=0; i < allCookieFields.size(); i++) {
					List<HttpCookie> cookies = HttpCookie.parse(allCookieFields.get(i));
					
					for(int j=0; j < cookies.size(); j++) {
						HttpCookie cookie = cookies.get(j);
						String cookieKey = cookie.getName();
						String cookieValue = cookie.getValue();
						if(cookieKey.equals("saml")) {
							samlCookie = cookieValue;
						}
					}
				}
				
				loginProgress.setProgress(6);
				
				if(urlConnection.getResponseCode() == 200 && samlCookie != null) {
					username = usernameStr;
					return context.getResources().getString(R.string.login_notice_success);
				}
				
				
			} catch (Exception e) {
				return context.getResources().getString(R.string.login_notice_failure);
			} finally {
				urlConnection.disconnect();
			}
			
			return context.getResources().getString(R.string.login_notice_success);
			
		}
		
		@Override
		protected void onPostExecute(String result) 
		{
			
			loginProgress.setProgress(7);
			
			if(result.equals(context.getResources().getString(R.string.login_notice_success)))
			{
				loginNotice.setTextColor(context.getResources().getColor(R.color.success_color));
				loginNotice.setText(result);
				
				Intent intent = new Intent(context, Welcome.class);
				intent.putExtra("username", username);
				intent.putExtra("samlCookie", samlCookie);
				
				loginNotice.setTextColor(context.getResources().getColor(R.color.plain));
				loginNotice.setText(context.getResources().getString(R.string.login_notice_initial_text));
				usernameField.setText("");
				passwordField.setText("");
				loginProgress.setVisibility(View.INVISIBLE);
				
				
				startActivity(intent);
			}
			else 
			{			
				//the color is set to red because if the user continues to stay on this activity then there has to have been some sort of error
		    	loginNotice.setTextColor(context.getResources().getColor(R.color.error_color));
		    	loginNotice.setText(result);
		    	loginProgress.setProgress(0);
		    	loginProgress.setVisibility(View.INVISIBLE);
			}
			
	     }
		
    }
    
    
    public boolean isNetworkAvailable() 
    {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        // if no network is available networkInfo will be null
        // otherwise check if we are connected
        if (networkInfo != null && networkInfo.isConnected()) {
            return true;
        }
        return false;
    }

}
