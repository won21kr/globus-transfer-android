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

import org.globusonline.transfer.JSONTransferAPIClient;
import org.globusonline.transfer.JSONTransferAPIClient.Result;
import org.json.JSONObject;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

public class DashboardHomeFragment extends Fragment {
	
	protected String mUsername = null;
	protected String mSamlCookie = null;

	private JSONTransferAPIClient client;
	
	private Activity context;
	
	private TextView welcomeUsername;
	private TextView taskSummary;
	private TextView taskSummaryContent;
	private ProgressBar taskSummaryProgress;
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, 
							 Bundle savedInstanceState) {
		if(container == null) {
			return null;  //no need to return a View if there is no parent
						  //container visible to put it in
		}
		
		Bundle args = getArguments();
		
		mUsername = args.getString("username");
		mSamlCookie = args.getString("samlCookie");
		
		View V = (ScrollView)inflater.inflate(R.layout.fragment_dashboard_home,  
											  container, false);
		
	    context = getActivity();
		
		welcomeUsername = (TextView) V.findViewById(R.id.welcome_username);
	    taskSummary = (TextView) V.findViewById(R.id.task_summary);
	    taskSummaryContent 
	    	= (TextView) V.findViewById(R.id.task_summary_content);
	    taskSummaryProgress 
	    	= (ProgressBar) V.findViewById(R.id.home_task_summary_progress_bar);
	    
	    taskSummaryProgress.setMax(6);  //6 steps to do a full log in
	    
	    try {
			client = new JSONTransferAPIClient(mUsername, null, mSamlCookie);
		} catch (Exception e) {
			e.printStackTrace();
		}
	    
	    welcomeUsername.append(mUsername);
	    
	    //init load of transfers (loads the default num of tasks from the API)
		new TransferAPILoadTaskSummary().execute();
		
		return V;
	}
	
	
	private class TransferAPILoadTaskSummary 
		extends AsyncTask<String, Void, String> 
	{
		
		@Override
		protected void onPreExecute() 
		{
			taskSummary.setTextColor(
					context.getResources().getColor(R.color.attempt_color));
			taskSummary.setText(R.string.home_task_summary_loading);
			taskSummaryProgress.setVisibility(View.VISIBLE);
		}
		
		
		@Override
		protected String doInBackground(String... params) 
		{
			
			taskSummaryProgress.setProgress(1);
			
			try 
			{
				Result r = client.getResult("/tasksummary");
				
				taskSummaryProgress.setProgress(2);
				
				JSONObject jO = r.document;
				String output = "";
				
				taskSummaryProgress.setProgress(3);
				
				String[] keys = { "active", "inactive", "succeeded", "failed" };
				String[] labels = { context.getResources().getString(R.string.home_task_summary_active_label),
									context.getResources().getString(R.string.home_task_summary_inactive_label),
									context.getResources().getString(R.string.home_task_summary_succeeded_label),
									context.getResources().getString(R.string.home_task_summary_failed_label) };
				
				if(!jO.getString("DATA_TYPE").equals("tasksummary"))
					return context.getResources().getString(R.string.api_result_error);
				
				taskSummaryProgress.setProgress(4);
				
				for(int i=0; i<keys.length; i++) 
				{
					if(i!=0)
						output+="\n";
					output += labels[i] + ": " + jO.getString(keys[i]);
				}
				
				taskSummaryProgress.setProgress(5);
				
				return output;
				
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
			
			return context.getResources().getString(R.string.api_result_error);
			
		}
		
		
		@Override
		protected void onPostExecute(String result) 
		{
			taskSummaryProgress.setProgress(6);
			
			taskSummaryContent.setText(result);
			taskSummaryContent.setVisibility(View.VISIBLE);
			taskSummaryProgress.setVisibility(View.GONE);
			
			if(result.equals(context.getResources().getString(R.string.api_result_error))) {
				taskSummaryContent.setTextColor(context.getResources().getColor(R.color.error_color));
			}
			
			taskSummary.setTextColor(context.getResources().getColor(R.color.plain));
			taskSummary.setText(R.string.home_task_summary_title);
		}
		
	}

}