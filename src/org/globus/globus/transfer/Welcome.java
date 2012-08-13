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

import java.util.List;
import java.util.Vector;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class Welcome extends FragmentActivity 
{
	
	private PagerAdapter mPagerAdapter;
	protected Context context;
	protected String mUsername;
	protected String mSamlCookie;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		super.setContentView(R.layout.activity_welcome);
		context = this;
		
		Intent incomingIntent = getIntent();
	    mUsername = incomingIntent.getStringExtra("username");
	    mSamlCookie = incomingIntent.getStringExtra("samlCookie");
		
		//Initialize the pager
		this.initializePaging();
	}
    
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) 
    {
      super.onConfigurationChanged(newConfig);
    }
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) 
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }
    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) 
    {
        switch (item.getItemId()) {
            case R.id.logout:
            	attemptLogout();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
	
	//Init the fragments to be paged
	private void initializePaging() 
	{
		Bundle args = new Bundle();
		args.putString("username", mUsername);
		args.putString("samlCookie", mSamlCookie);
		
		List<Fragment> fragments = new Vector<Fragment>();
		//fragments.add(Fragment.instantiate(this, DashboardHomeFragment.class.getName(), args));
		fragments.add(Fragment.instantiate(this, ManageTransfersFragment.class.getName(), args));
		//fragments.add(Fragment.instantiate(this, StartTransferFragment.class.getName(), args));
		this.mPagerAdapter  = new PagerAdapter(super.getSupportFragmentManager(), fragments, context);

		ViewPager pager = (ViewPager) super.findViewById(R.id.viewpager);
		pager.setAdapter(this.mPagerAdapter);
	}
	
	
	private void attemptLogout()
	{
		AlertDialog.Builder builder = 
				new AlertDialog.Builder(context);
		
		builder
			.setMessage(context.getResources().getString(
					R.string.confirm_logout))
			.setCancelable(false)
			.setPositiveButton(context.getResources().getString(R.string.yes), 
					new DialogInterface.OnClickListener() 
			{
				
				public void onClick(DialogInterface dialog, 
									int which) 
				{
					Welcome.this.finish();
				}
			})
			.setNegativeButton(context.getResources().getString(R.string.no), 
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
	
}