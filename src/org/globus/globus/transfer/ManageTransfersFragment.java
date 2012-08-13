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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.globusonline.transfer.JSONTransferAPIClient;
import org.globusonline.transfer.JSONTransferAPIClient.Result;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ManageTransfersFragment extends ListFragment 
{
	
	private static Activity context;
	
	private String mUsername = null;
	private String mSamlCookie = null;
	
	private static JSONTransferAPIClient client;
	
	private TextView manageTransfersAltText;
	private ProgressBar manageTransfersProgress;
	
	private static ListView manageTransfersList;
	private static ArrayAdapter<JSONObject> mAdapter = null;
	
	private static TaskDetailsDialogFragment mDialogFragment = null;
	
	private static int taskActionIndex;
	private static String taskActionString;
	
	private static boolean locked = false;
	private static boolean dialogIsDoingSomething = false;
	private static boolean loadingNewTasks = false;
	

	public View onCreateView(LayoutInflater inflater, ViewGroup container, 
							 Bundle savedInstanceState) 
	{
		if(container == null)  //no need to return a View if there
			return null;       //is no container to put it in
		
		context = getActivity();
		
		Bundle args = getArguments();
		mUsername = args.getString("username");
		mSamlCookie = args.getString("samlCookie");
		
		View V = (LinearLayout) inflater.inflate(
				R.layout.fragment_manage_transfers, container, false);
		ListView manageTransfersList = (ListView) V.findViewById(android.R.id.list);
		
		manageTransfersAltText = (TextView) V.findViewById(
				R.id.manage_transfers_alt_text);
		manageTransfersProgress = (ProgressBar) V.findViewById(
				R.id.manage_transfers_progress_bar);
		
		try 
		{
			client = new JSONTransferAPIClient(mUsername, null, mSamlCookie);
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		
		new TransferAPILoadManageTransfers().execute();
		
		int delay = 5000;   // delay for 5 sec.
		int period = 20000;  // repeat every 30 sec.
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() 
			{
		        public void run() 
		        {
		        	if(!loadingNewTasks)
		        	{
			        	loadingNewTasks = true;
			        	new TransferAPILoad10MoreNewTasks().execute();
		        	}
		        }
			}, delay, period);
		
		int intervalForRefresh = 7000;
		Timer refresher = new Timer();
		refresher.scheduleAtFixedRate(new TimerTask()
			{
				public void run() 
		        {
		        	new TransferAPIRefreshTasks().execute();
		        }
			}, intervalForRefresh, intervalForRefresh);
		
		return V;
	}
	
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		manageTransfersList = getListView();
		manageTransfersList.setOnScrollListener(new EndlessScrollListener(10));
	}
    
    
    @Override
    public void onConfigurationChanged(Configuration  newConfig) 
    {
      super.onConfigurationChanged(newConfig);

    }
	
	
	public class EndlessScrollListener implements OnScrollListener 
	{
		private int visibleThreshold = 5;
		private int previousTotal = 0;
		private boolean loading = true;
		
		public EndlessScrollListener() { }
		public EndlessScrollListener(int visibleThreshold)
		{
			this.visibleThreshold = visibleThreshold;
		}

		public void onScroll(AbsListView view, int firstVisibleItem,
				int visibleItemCount, int totalItemCount) 
		{
			if (loading) 
            {
                if (totalItemCount > previousTotal) 
                {
                    loading = false;
                    previousTotal = totalItemCount;
                }
            }
            if (!loading && (totalItemCount - visibleItemCount) 
            		<= (firstVisibleItem + visibleThreshold)) 
            {
                new TransferAPILoad10MoreTasks().execute();
                loading = true;
            }
		}

		public void onScrollStateChanged(AbsListView view, int scrollState) { }
    }
	
	
	public class ManageTransfersAdapter extends ArrayAdapter<JSONObject> {

		private Context context;
		private List<JSONObject> tasks;
		
		
		public ManageTransfersAdapter(Context context, int resource, 
									  List<JSONObject> objects) 
		{
			super(context, resource, objects);
			this.tasks = objects;
			this.context = context;
		}
		
		
		@Override
		public View getView(final int position, View convertView, 
							ViewGroup parent) 
		{

			JSONObject task = (JSONObject) this.getItem(position);
			View row = convertView;
			
			if (row == null) {
                LayoutInflater vi = 
                		(LayoutInflater) context.getSystemService(
                				Context.LAYOUT_INFLATER_SERVICE);
                row = vi.inflate(R.layout.list_manage_transfers, null);
            }
			
			String status = null;
			String taskTitle = null;
			String taskLabel = null;
			String transferType = null;
			String sourceEndpoint = null;
			String destinationEndpoint = null;
			String taskRoute = null;
			String niceStatus = null;
			
			Integer niceStatusExpiresIn = null;
			int subtasksSucceeded = 0;
			int subtasksPending = 0;
			int subtasksTotal = 0;
			int files = 0;
			int filesSkipped = 0;
			int filesUpdated = 0;
			
			boolean anySubtasksCanceled = false;
			boolean sync = false;
			
			boolean valuesSet = false;

			TextView taskTransferTitle = (TextView) row.findViewById(
					R.id.manage_transfers_task_title);
			TextView taskProgressDetails = (TextView) row.findViewById(
					R.id.manage_transfers_task_transfer_progress_status_text);
			TextView taskRouteDisplay = (TextView) row.findViewById(
					R.id.manage_transfers_task_transfer_route);
			
			ImageView taskStatusIcon = (ImageView) row.findViewById(
					R.id.manage_transfers_task_status);
			
			ProgressBar taskProgressBar = (ProgressBar) row.findViewById(
					R.id.manage_transfers_task_transfer_progress_bar);
			
			try 
			{
				if(!task.isNull("status"))
					status = task.getString("status");
				
				if(task.isNull("label")) 
				{
					taskTitle = context.getResources().getString(
							R.string.manage_transfers_click_to_add_label);
					taskTransferTitle.setTextColor(
							context.getResources().getColor(R.color.light));
				}
				else 
				{
					taskLabel = task.getString("label");
					taskTitle = taskLabel;
					taskTransferTitle.setTextColor(
							context.getResources().getColor(R.color.plain));
				}
				
				if(!task.isNull("type"))
					transferType = task.getString("type");
				
				if(!task.isNull("source_endpoint"))
					sourceEndpoint = task.getString("source_endpoint");
				
				if(!task.isNull("destination_endpoint"))
					destinationEndpoint = task.getString(
							"destination_endpoint");
				
				taskRoute = sourceEndpoint + " to " + destinationEndpoint;
				
				if(!task.isNull("nice_status"))
					niceStatus = task.getString("nice_status");
				
				if(!task.isNull("nice_status_expires_in"))
					niceStatusExpiresIn = task.getInt(
							"nice_status_expires_in");
				
				if(!task.isNull("subtasks_succeeded"))
					subtasksSucceeded = task.getInt("subtasks_succeeded");
				
				if(!task.isNull("subtasks_pending"))
					subtasksPending = task.getInt("subtasks_pending");
				
				subtasksTotal = subtasksSucceeded + subtasksPending;
				
				if(!task.isNull("files"))
					files = task.getInt("files");
				
				if(!task.isNull("files_skipped"))
					filesSkipped = task.getInt("files_skipped");
				
				filesUpdated = files - filesSkipped;
				
				if(!task.isNull("subtasks_canceled"))
					if(task.getInt("subtasks_canceled") != 0)
						anySubtasksCanceled = true;
				
				if(!task.isNull("sync_level"))
					if(task.getInt("sync_level") != 0)
						sync = true;
				
				valuesSet = true;
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
			
			if(valuesSet) 
			{
				taskProgressBar.setMax(subtasksTotal);
				taskProgressBar.setProgress(subtasksSucceeded);
				taskRouteDisplay.setVisibility(View.GONE);
				taskProgressBar.setVisibility(View.GONE);
				
				if(transferType != null && taskRoute != null) 
				{
					if(transferType.equals("TRANSFER"))
						taskRouteDisplay.setText(taskRoute);
					else if(transferType.equals("DELETE"))
						taskRouteDisplay.setText(context.getResources()
								.getString(R.string.manage_transfers_task_on) 
								           + sourceEndpoint);
				}
				
				//check what is the status and 
				//set the appropriate icon for that
				if(status.equals("SUCCEEDED")) 
				{
					if(transferType.equals("TRANSFER")) 
					{
						if(sync) 
						{
							if(files == 1)
								taskProgressDetails.setText(files + " " + context.getResources().getString(R.string.manage_transfers_task_single_file_checked));
							else
								taskProgressDetails.setText(files + " " + context.getResources().getString(R.string.manage_transfers_task_multiple_files_checked));
							
							taskProgressDetails.append(" ");
							
							if(filesUpdated == 0)
								taskProgressDetails.append(context.getResources().getString(R.string.manage_transfers_task_no_files_updated));
							else if(filesUpdated == 1)
								taskProgressDetails.append(filesUpdated + " " + context.getResources().getString(R.string.manage_transfers_task_single_file_updated));
							else
								taskProgressDetails.append(filesUpdated + " " + context.getResources().getString(R.string.manage_transfers_task_multiple_files_updated));
						}
						else 
						{
							if(files == 1)
								taskProgressDetails.setText(files + " " + context.getResources().getString(R.string.manage_transfers_task_file_count_single));
							else
								taskProgressDetails.setText(files + " " + context.getResources().getString(R.string.manage_transfers_task_file_count_multiple));
						}
	
						taskProgressDetails.append(" " + context.getResources().getString(R.string.manage_transfers_task_from) + " " + taskRoute);
					}
					else if(transferType.equals("DELETE")) 
					{
						if(files == 1)
							taskProgressDetails.setText(files + " " + context.getResources().getString(R.string.manage_transfers_task_deleted_single_file));
						else
							taskProgressDetails.setText(files + " " + context.getResources().getString(R.string.manage_transfers_task_deleted_multiple_files));
						
						taskProgressDetails.append(" " + context.getResources().getString(R.string.manage_transfers_task_from) + " " + sourceEndpoint);
					}
					
					taskStatusIcon.setImageResource(R.drawable.transfer_complete);
				}
				else if(status.equals("FAILED")) 
				{
					if(transferType.equals("TRANSFER")) 
					{
						taskProgressDetails.setText(context.getResources().getString(R.string.manage_transfers_task_transfer));
					}
					else if(transferType.equals("DELETE")) 
					{
						taskProgressDetails.setText(context.getResources().getString(R.string.manage_transfers_task_delete));
					}
					
					taskProgressDetails.append(" ");
					
					if(niceStatus!=null) 
					{
						taskStatusIcon.setImageResource(R.drawable.transfer_obstacle);
						taskProgressDetails.append(niceStatus);
						taskProgressBar.setVisibility(View.VISIBLE);
					}
					else 
					{
						if(anySubtasksCanceled) 
						{
							taskStatusIcon.setImageResource(R.drawable.transfer_cancelled);
							taskProgressDetails.append(context.getResources().getString(R.string.manage_transfers_task_cancelled));
						} 
						else 
						{
							taskStatusIcon.setImageResource(R.drawable.transfer_failed);
							taskProgressDetails.append(context.getResources().getString(R.string.manage_transfers_task_failed));
						}
					}
					
					taskProgressDetails.append(" ");
					
					if(transferType.equals("TRANSFER")) 
					{
						taskProgressDetails.append(context.getResources().getString(R.string.manage_transfers_task_from) + " " + taskRoute);
					}
					else if(transferType.equals("DELETE")) 
					{
						taskProgressDetails.append(context.getResources().getString(R.string.manage_transfers_task_on) + " " + sourceEndpoint);
					}
				}
				else if(status.equals("ACTIVE")) 
				{
					taskProgressBar.setVisibility(View.VISIBLE);
					taskRouteDisplay.setVisibility(View.VISIBLE);
					int niceStatusExpiresInValue = niceStatusExpiresIn.intValue();
					
					if(niceStatusExpiresInValue > 7200 || niceStatusExpiresInValue < 0) 
					{
						taskStatusIcon.setImageResource(R.drawable.transfer_in_progress);
						
						if(transferType.equals("TRANSFER"))
							taskProgressDetails.setText(context.getResources().getString(R.string.manage_transfers_task_transfer));
						else if(transferType.equals("DELETE"))
							taskProgressDetails.setText(context.getResources().getString(R.string.manage_transfers_task_delete));
						
						taskProgressDetails.append(" " + context.getResources().getString(R.string.manage_transfers_task_in_progress));
						
					}
					else if(niceStatusExpiresInValue > 0 && niceStatusExpiresInValue < 7200) 
					{
						taskStatusIcon.setImageResource(R.drawable.transfer_warning);
						taskProgressBar.setProgressDrawable(context.getResources().getDrawable(R.drawable.warningprogressbar));
						taskProgressDetails.setText(context.getResources().getString(R.string.manage_transfers_task_creds_expiring_soon));
					}
					
				}
				else if(status.equals("INACTIVE")) 
				{
					taskStatusIcon.setImageResource(R.drawable.transfer_obstacle);
					if(niceStatus != null) 
					{
						taskProgressBar.setVisibility(View.VISIBLE);
						taskProgressBar.setProgressDrawable(context.getResources().getDrawable(R.drawable.failprogressbar));
						if(niceStatus.equals("Queued")) 
						{
							taskStatusIcon.setImageResource(R.drawable.transfer_queued);
							taskProgressDetails.setText(context.getResources().getString(R.string.manage_transfers_task_queued));
							taskProgressBar.setProgressDrawable(context.getResources().getDrawable(R.drawable.regularprogressbar));
						}
						else if(niceStatus.equals("Creds Expired"))
							taskProgressDetails.setText(context.getResources().getString(R.string.manage_transfers_task_creds_expired));
						else
							taskProgressDetails.setText(niceStatus);
					}
					else
						taskProgressDetails.setText(context.getResources().getString(R.string.manage_transfers_task_transfer) + " " + context.getResources().getString(R.string.manage_transfers_task_failed));
				}
				
				
				taskTransferTitle.setText(taskTitle);
			}
			
			return row;
		}
		
		public int getCount() 
		{
			return tasks.size();
		}
		
		public JSONObject getItem(int index) 
		{
			return tasks.get(index);
		}
		
		public long getItemId(int index) 
		{
			return index;
		}
		
	}
	
	
	public void onListItemClick(ListView l, View v, int position, long id) 
	{
	    FragmentTransaction ft = getFragmentManager().beginTransaction();
	    Fragment prev = getFragmentManager().findFragmentByTag("dialog");
	    if (prev != null) {
	        ft.remove(prev);
	    }
	    ft.addToBackStack(null);
	    
	    locked = true;
	    mDialogFragment = TaskDetailsDialogFragment.newInstance(position);
	    mDialogFragment.show(ft, "dialog");
	}
	
	
	public static class TaskDetailsDialogFragment extends DialogFragment 
	{
		
		int mTaskIndex;
		
		String label = "";
		String taskId = null;
		String labelInputText = null;
		
		EditText labelView = null;
		
		
		public static TaskDetailsDialogFragment newInstance(int index) 
		{
			TaskDetailsDialogFragment f = new TaskDetailsDialogFragment();
			
			//supply the index
			Bundle args = new Bundle();
			args.putInt("index", index);
			f.setArguments(args);
			
			return f;
		}
		
		
		@Override
		public void onCreate(Bundle savedInstanceState) 
		{
			super.onCreate(savedInstanceState);
			mTaskIndex = getArguments().getInt("index");
			setStyle(DialogFragment.STYLE_NO_TITLE, 
					 android.R.style.Theme_Holo_Dialog);
		}
		
		
		@Override
		public void onDismiss(DialogInterface dialog)
		{
			super.onDismiss(dialog);
			if(!dialogIsDoingSomething)
				locked = false;
		}
		
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, 
								 Bundle savedInstanceState) 
		{
			View v = inflater.inflate(R.layout.fragment_task_details, 
									  container, false);
			
			final JSONObject task = mAdapter.getItem(mTaskIndex);
			
			labelView = (EditText) v.findViewById(R.id.task_details_title);
			
			TextView taskIdView = (TextView) v.findViewById(
					R.id.task_details_task_id);
			TextView statusView = (TextView) v.findViewById(
					R.id.task_details_status_value);
			TextView startView = (TextView) v.findViewById(
					R.id.task_details_start_value);
			TextView endView = (TextView) v.findViewById(
					R.id.task_details_end_value);
			TextView originView = (TextView) v.findViewById(
					R.id.task_details_origin_value);
			TextView destView = (TextView) v.findViewById(
					R.id.task_details_dest_value);
			TextView bytesView = (TextView) v.findViewById(
					R.id.task_details_bytes_value);
			TextView filesView = (TextView) v.findViewById(
					R.id.task_details_files_value);
			
			String status = null;
			String start = null;
			String end = null;
			String origin = null;
			String dest = null;
			
			Integer bytes = null;
			Integer files = null;
			Integer subtasksPending = null;
			
			
			try 
			{
				if(!task.isNull("nice_status"))
					status = task.getString("nice_status");
				
				if(!task.isNull("request_time"))
					start = task.getString("request_time");
				
				if(!task.isNull("completion_time"))
						end = task.getString("completion_time");
				
				if(!task.isNull("source_endpoint"))
					origin = task.getString("source_endpoint");
				
				if(!task.isNull("destination_endpoint"))
					dest = task.getString("destination_endpoint");
				
				if(!task.isNull("bytes_transferred"))
					bytes = new Integer((task.getInt("bytes_transferred")));
				
				if(!task.isNull("files"))
					files = new Integer(task.getInt("files"));
				
				if(!task.isNull("subtasks_pending"))
					subtasksPending = new Integer(task.getInt(
						"subtasks_pending"));
				
				if(!task.isNull("label"))
					label = task.getString("label");
				
				if(!task.isNull("task_id"))
					taskId = task.getString("task_id");
				
			} catch(Exception e) {
				e.printStackTrace();
			}
			
			if(label != null)
				labelView.setText(label);
			
			if(taskId != null)
				taskIdView.setText(taskId);
			
			if(status != null)
				statusView.setText(status);
			else
				statusView.setText(context.getResources().getString(
						R.string.not_available_text));
			
			if(start != null)
				startView.setText(start);
			
			if(end != null)
				endView.setText(end);
			else
				endView.setText(context.getResources().getString(
						R.string.task_details_task_not_complete));
			
			if(origin != null)
				originView.setText(origin);
			
			if(dest != null)
				destView.setText(dest);
			else
				destView.setText(context.getResources().getString(
						R.string.not_available_text));
			
			if(bytes != null)
				bytesView.setText(bytes.toString());
			else
				bytesView.setText(context.getResources().getString(
						R.string.not_available_text));
			
			if(files != null)
				filesView.setText(files.toString());
			else
				filesView.setText(context.getResources().getString(
						R.string.not_available_text));
			
			final TextView cancel = (TextView) v.findViewById(
					R.id.task_details_cancel_task);
			cancel.setOnClickListener(new OnClickListener() 
			{
				
				public void onClick(View v) 
				{
					AlertDialog.Builder builder = 
							new AlertDialog.Builder(context);
					
					//set title
					builder.setTitle(context.getResources().getString(
							R.string.task_details_cancel_task_title));
					
					builder
						.setMessage(context.getResources().getString(
								R.string.task_details_cancel_task_dialog))
						.setCancelable(false)
						.setPositiveButton(context.getResources().getString(
								R.string.yes), 
								new DialogInterface.OnClickListener() 
						{
							
							public void onClick(DialogInterface dialog, 
												int which) 
							{
								dialogIsDoingSomething = true;
								mDialogFragment.dismiss();
								
								taskActionIndex = mTaskIndex;
								new TransferAPICancelTask().execute(taskId);
							}
						})
						.setNegativeButton(context.getResources().getString(
								R.string.no), 
								new DialogInterface.OnClickListener() 
						{
							public void onClick(DialogInterface dialog, 
									int which) 
							{
								//"as you were", so therefore do nothing
							}
							
						});
						
					AlertDialog confirm = builder.create();
					confirm.show();
					
				}
				
			});
			
			if(subtasksPending.intValue() == 0 || subtasksPending == null)
				cancel.setVisibility(View.GONE);
			
			
	        final Button button = (Button) v.findViewById(
	        		R.id.task_details_ok_button);
	        button.setOnClickListener(new OnClickListener() 
	        {
	            public void onClick(View v) 
	            {
	            	labelInputText = labelView.getText().toString();
	            	
	            	if(!labelInputText.equals(label) && label != null) 
	            	{
	            		dialogIsDoingSomething = true;
	            		mDialogFragment.dismiss();
	            		
	            		taskActionString = labelInputText;
	            		taskActionIndex = mTaskIndex;
	            		
	            		new TransferAPIUpdateLabel().execute(taskId, 
	            											 labelInputText);
	            	}
	            	else
	            		mDialogFragment.dismiss();
	            }
	        });
			
			labelView.addTextChangedListener(new TextWatcher() {

				public void afterTextChanged(Editable s) {}
				public void beforeTextChanged(CharSequence s, int start, 
											  int count, int after) {}
				public void onTextChanged(CharSequence s, int start,
						int before, int count) 
				{
					if(!s.toString().equals(label)) 
					{
						button.setText(context.getResources().getString(
								R.string.save_and_close_button_text));
					}
					else 
					{
						button.setText(context.getResources().getString(
								R.string.close_button_text));
					}
				}
				
			});
			
			return v;
		}
	}
	
	
	private static class TransferAPICancelTask 
				   extends AsyncTask<String, Void, String> 
	{
		
		protected ProgressDialog waiting = new ProgressDialog(context);
		
		
		@Override
		protected void onPreExecute() 
		{
			waiting = ProgressDialog.show(
			  context, "", context.getResources().getString(
					  	   R.string.task_details_cancel_task_dialog_wait),
			  false, false);
		}
		
		
		@Override
		protected String doInBackground(String... params) {
			
			String taskId = params[0];
			String resource = "/task/" + taskId + "/cancel";
			
			try 
			{
				Result r = client.postResult(resource, null);
				JSONObject jO = r.document;
				
				if(!jO.getString("DATA_TYPE").equals("result")) {
					return null;
				}
				
				String code = jO.getString("code");
				
				if(code.equals("Canceled"))
					return context.getResources().getString(
							R.string.task_details_cancel_task_successful);
				else if(code.equals("TaskComplete"))
					return context.getResources().getString(
							R.string.task_details_cancel_task_failed_due_to_task_complete);
					
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(String result) 
		{
			JSONObject task = mAdapter.getItem(taskActionIndex);
			
			if(result == null)
				Toast.makeText(context, context.getResources().getString(
						R.string.task_details_cancel_failed), 
						Toast.LENGTH_LONG).show();
			else if(result.equals(
					context.getResources().getString(
							R.string.task_details_cancel_task_successful)))
			{
				mAdapter.remove(task);
				task.remove("status");
				task.remove("subtasks_canceled");
				task.remove("nice_status");
				task.remove("subtasks_pending");
				
				try 
				{
					task.put("status", "FAILED");
					task.put("subtasks_canceled", 1);
					task.put("subtasks_pending", 0);
				} 
				catch (Exception e) {
					e.printStackTrace();
				}
				
				mAdapter.insert(task, taskActionIndex);
				mAdapter.notifyDataSetChanged();
				
				Toast.makeText(context, result, Toast.LENGTH_LONG).show();
			}
			else if(result.equals(
					context.getResources().getString(
							R.string.task_details_cancel_task_failed_due_to_task_complete)))
			{	
				mAdapter.remove(task);
				task.remove("status");
				task.remove("subtasks_canceled");
				task.remove("nice_status");
				task.remove("subtasks_pending");
				
				try 
				{
					task.put("status", "SUCCEEDED");
					task.put("subtasks_canceled", 0);
					task.put("subtasks_pending", 0);
				} 
				catch (Exception e) {
					e.printStackTrace();
				}
				
				mAdapter.insert(task, taskActionIndex);
				mAdapter.notifyDataSetChanged();
				
				Toast.makeText(context, result, Toast.LENGTH_LONG).show();
			}
			
			String taskId = null;
			if(!task.isNull("task_id"))
			{
				try {
					taskId = task.getString("task_id");
				} catch (JSONException e1) {
					e1.printStackTrace();
				}
			}
			
			new TransferAPIUpdateTask().execute(taskId);
			waiting.dismiss();
			dialogIsDoingSomething = false;
		}
		
	}
	
	
	private static class TransferAPIUpdateLabel 
				   extends AsyncTask<String, Void, String> {
		
		protected ProgressDialog waiting = new ProgressDialog(context);
		
		
		@Override
		protected void onPreExecute() 
		{
			waiting = ProgressDialog.show(
			  context, "", context.getResources().getString(
					  	 R.string.task_details_task_label_update__dialog_wait),
			  false, false);
		}
		
		
		@Override
		protected String doInBackground(String... params) {
			
			String newLabel = params[1];
			String taskId = params[0];
			String resource = "/task/" + taskId;
			
			JSONObject requestParams = new JSONObject();
			
			try 
			{
				requestParams.put("DATA_TYPE", "task");
				
				if(!newLabel.equals(""))
				{
					requestParams.put("label", newLabel);
				}
				else
				{
					requestParams.put("label", JSONObject.NULL);
				}
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
			
			try 
			{
				Result r = client.putResult(resource, requestParams);
				JSONObject jO = r.document;
				if(jO.getString("message").equals(
						"Updated task label successfully"))
					return newLabel;
					
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
			
			return null;
		}
		
		
		@Override
		protected void onPostExecute(String result) 
		{
			JSONObject task = mAdapter.getItem(taskActionIndex);
			
			if(result == null)
				Toast.makeText(context, context.getResources().getString(
						R.string.task_details_task_label_update_failed), 
						Toast.LENGTH_LONG).show();
			else 
			{
				if(result.equals(""))
					Toast.makeText(context, context.getResources().getString(
							R.string.task_details_task_label_cleared), 
							Toast.LENGTH_SHORT).show();
				else
					Toast.makeText(context, context.getResources().getString(
							R.string.task_details_task_label_updated) + ": " + 
							result, Toast.LENGTH_LONG).show();
				
				mAdapter.remove(task);
    			task.remove("label");
    			
    			try 
    			{
    				if(taskActionString.equals(""))
    					task.put("label", JSONObject.NULL);
    				else
    					task.put("label", taskActionString);
				} 
    			catch (Exception e) {
					e.printStackTrace();
				}
    			
    			mAdapter.insert(task, taskActionIndex);
    			mAdapter.notifyDataSetChanged();
			}
			
			String taskId = null;
			if(!task.isNull("task_id"))
			{
				try {
					taskId = task.getString("task_id");
				} catch (JSONException e1) {
					e1.printStackTrace();
				}
			}
			
			new TransferAPIUpdateTask().execute(taskId);
			waiting.dismiss();
			dialogIsDoingSomething = false;
		}
	}
	
	
	public class TransferAPILoadManageTransfers 
		   extends AsyncTask<String, Void, List<JSONObject>> 
	{
		
		@Override
		protected void onPreExecute() 
		{
			locked = true;
			
			manageTransfersAltText.setTextColor(context.getResources()
					.getColor(R.color.attempt_color));
			manageTransfersAltText.setText(R.string.manage_transfers_loading);
			manageTransfersAltText.setVisibility(View.VISIBLE);
			manageTransfersProgress.setVisibility(View.VISIBLE);
		}
		
		
		@Override
		protected List<JSONObject> doInBackground(String... params) 
		{
			
			manageTransfersProgress.setProgress(1);
			List<JSONObject> result = new ArrayList<JSONObject>();
			
			try 
			{
				
				Map<String, String> requestParams = 
						new HashMap<String, String>();
				requestParams.put("filter", "type:TRANSFER,DELETE");
				
				Result r = client.getResult("/task_list", requestParams);
				manageTransfersProgress.setProgress(2);
				
				JSONObject jO = r.document;
				manageTransfersProgress.setProgress(3);
				
				if(!jO.getString("DATA_TYPE").equals("task_list")) 
				{
					return null;
				}
				
				manageTransfersProgress.setProgress(4);
				
				JSONArray jA = jO.getJSONArray("DATA");
				
				for(int i=0; i < jA.length(); i++) 
				{
					JSONObject task = jA.getJSONObject(i);
					result.add(task);
				}
				
				manageTransfersProgress.setProgress(5);
				
				return result;
				
				
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
			
			return null;
			
		}
		
		
		@Override
		protected void onPostExecute(List<JSONObject> result) 
		{
			
			manageTransfersProgress.setProgress(6);
			manageTransfersProgress.setVisibility(View.GONE);
			
			if(result == null) 
			{
				manageTransfersAltText.setTextColor(context.getResources()
						.getColor(R.color.error_color));
				manageTransfersAltText.setText(context.getResources()
						.getString(R.string.api_result_error));
			}
			else 
			{
				manageTransfersAltText.setVisibility(View.GONE);
				mAdapter = new ManageTransfersAdapter(context, 
						android.R.layout.simple_list_item_1, result);
				setListAdapter(mAdapter);
			}
			
			locked = false;
		}
		
	}
	
	
	public class TransferAPILoad10MoreTasks 
	   extends AsyncTask<Void, Void, List<JSONObject>> 
	{
		
		@Override
		protected List<JSONObject> doInBackground(Void... params) 
		{
			List<JSONObject> result = new ArrayList<JSONObject>();
			JSONObject lastListedItem
				= mAdapter.getItem(mAdapter.getCount()-1);
			
			try 
			{
				
				Map<String, String> requestParams = 
						new HashMap<String, String>();
				String requestTime = null;
				if(!lastListedItem.isNull("request_time"))
					requestTime = lastListedItem.getString("request_time");
				requestParams.put("filter", "type:TRANSFER,DELETE/" +
						"request_time:," + requestTime);
				
				Result r = client.getResult("/task_list", requestParams);
				
				JSONObject jO = r.document;
				
				if(!jO.getString("DATA_TYPE").equals("task_list")) 
				{
					return null;
				}
				
				JSONArray jA = jO.getJSONArray("DATA");
				
				for(int i=0; i < jA.length(); i++) 
				{
					JSONObject task = jA.getJSONObject(i);
					result.add(task);
				}
				
				if(result.size() >= 1)
					result.remove(0);
				
				return result;
				
				
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
			
			return null;
		}
		
		
		protected void onPostExecute(List<JSONObject> result) 
		{
			
			if(result == null) 
			{
				//Toast.makeText(context,
				//		context.getResources().getString(
				//				R.string.manage_transfers_load_more_failed),
				//		Toast.LENGTH_LONG).show();
			}
			else 
			{
				if(result.size() > 0)
				{
					for(int i=0; i < result.size(); i++)
					{
						mAdapter.add(result.get(i));
					}
					mAdapter.notifyDataSetChanged();
				}
			}
		}
	}
	
	
	public class TransferAPILoad10MoreNewTasks 
	   extends AsyncTask<Void, Void, List<JSONObject>> 
	{
		
		@Override
		protected List<JSONObject> doInBackground(Void... params) 
		{
			List<JSONObject> result = new ArrayList<JSONObject>();
			JSONObject firstListedItem = mAdapter.getItem(0);
			Map<String, String> requestParams = new HashMap<String, String>();
			
			try 
			{
				String requestTime = null;
				int numResultsReturned = 10;
				int resultsWanted = 10;
				
				if(!firstListedItem.isNull("request_time"))
					requestTime = firstListedItem.getString("request_time");
				
				while (requestTime != null 
						&& numResultsReturned == resultsWanted)
				{
					requestParams.put("filter", "type:TRANSFER,DELETE/" +
							"request_time:"+requestTime + ",");
					Result r = client.getResult("/task_list", requestParams);
					JSONObject jO = r.document;
					
					if(!jO.getString("DATA_TYPE").equals("task_list"))
						return null;
					
					numResultsReturned = 0;
					JSONArray jA = jO.getJSONArray("DATA");
					for(int i=0; i < jA.length(); i++) 
					{
						JSONObject task = jA.getJSONObject(i);
						result.add(task);
						numResultsReturned++;
					}
					
					if(numResultsReturned == resultsWanted)
					{
						JSONObject lastItem = null;
						if(result.size() >= 1)
							lastItem = result.get(result.size()-1);
						if(lastItem != null && !lastItem.isNull("request_time"))
							requestTime
								= lastItem.getString("request_time");
					}
				}
				
				if(result.size() >= 1)
					result.remove(result.size()-1);
				return result;
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
			
			return null;
		}
		
		
		protected void onPostExecute(List<JSONObject> result) 
		{
			
			if(result == null) 
			{
				//Toast.makeText(context,
				//		context.getResources().getString(
				//				R.string.manage_transfers_load_more_failed),
				//		Toast.LENGTH_LONG).show();
			}
			else 
			{
				if(result.size() > 0 && !locked)
				{
					for(int i=0; i < result.size(); i++)
					{
						mAdapter.insert(result.get((result.size()-1)-i), 0);
					}
					mAdapter.notifyDataSetChanged();
				}
			}
			
			loadingNewTasks = false;
		}
	}
	
	
	public static class TransferAPIUpdateTask 
	   extends AsyncTask<String, Void, JSONObject> 
	{
		
		protected ProgressDialog waiting = new ProgressDialog(context);
		
		
		@Override
		protected void onPreExecute() 
		{
			waiting = ProgressDialog.show(
			  context, "", context.getResources().getString(
					  	 R.string.task_refreshing_dialog),
			  false, false);
		}
		
		
		@Override
		protected JSONObject doInBackground(String... params) 
		{
			String taskId = params[0];
			String resource = "/task/" + taskId;
			
			try 
			{
				Result r = client.getResult(resource);
				JSONObject jO = r.document;
				
				if(!jO.getString("DATA_TYPE").equals("task")) 
					return null;
				
				return jO;
			} 
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
			return null;
		}
		
		
		protected void onPostExecute(JSONObject result) 
		{
			
			if(result != null) 
			{
				JSONObject oldTask = mAdapter.getItem(taskActionIndex);
				mAdapter.remove(oldTask);
				mAdapter.insert(result, taskActionIndex);
				mAdapter.notifyDataSetChanged();
			}
			
			waiting.dismiss();
			locked = false;
		}
	}
	
	
	public static class TransferAPIRefreshTasks 
	   extends AsyncTask<Void, Void, List<JSONObject>> 
	{
		
		private List<String> taskIds = new ArrayList<String>();
		
		
		@Override
		protected void onPreExecute()
		{
			//iterate through all of the show tasks and pick out the
			//ones that are either active or inactive
			
			for(int i=0; i < mAdapter.getCount(); i++)
			{
				JSONObject t = mAdapter.getItem(i);
				if(!t.isNull("task_id") && !t.isNull("status"))
				{
					try {
						String status = t.getString("status");
						if(status.equals("ACTIVE") || status.equals("INACTIVE"))
							taskIds.add(t.getString("task_id"));
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		
		@Override
		protected List<JSONObject> doInBackground(Void... params) 
		{
			StringBuilder builder = new StringBuilder();
			for(int i=0; i < taskIds.size(); i++)
			{
				builder.append(taskIds.get(i));
				if(i!=taskIds.size()-1)
					builder.append(",");
			}
			
			List<JSONObject> result = new ArrayList<JSONObject>();
			String resource = "/task_list";
			Map<String, String> requestParams = new HashMap<String, String>();
			requestParams.put("filter", "task_id:" + builder.toString());
			
			try 
			{
				Result r = client.getResult(resource, requestParams);
				JSONObject jO = r.document;
				
				if(!jO.getString("DATA_TYPE").equals("task_list")) 
					return null;
				
				JSONArray jA = jO.getJSONArray("DATA");
				
				for(int i=0; i < jA.length(); i++) 
				{
					JSONObject task = jA.getJSONObject(i);
					result.add(task);
				}
				
				return result;
			} 
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
			return null;
		}
		
		
		protected void onPostExecute(List<JSONObject> result) 
		{
			if(result != null && !locked && !loadingNewTasks) 
			{
				locked = true;
				for(int i=0; i < result.size(); i++)
				{
					JSONObject task = result.get(i);
					if(!task.isNull("task_id"))
					{
						try
						{
							String taskId = task.getString("task_id");
							for(int j=0; j < mAdapter.getCount(); j++)
							{
								String listedId = null;
								JSONObject listedTask = mAdapter.getItem(j);
								if(!listedTask.isNull("task_id"))
									listedId = listedTask.getString("task_id");
								
								if(listedId != null && taskId.equals(listedId))
								{
									mAdapter.remove(listedTask);
									mAdapter.insert(task, j);
								}
							}
						}
						catch(Exception e)
						{
							e.printStackTrace();
						}
						
						
					}
				}

				mAdapter.notifyDataSetChanged();
				locked = false;
			}
		}
	}
	
} /*PH*/