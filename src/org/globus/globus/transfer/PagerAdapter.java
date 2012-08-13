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

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

public class PagerAdapter extends FragmentPagerAdapter 
{
	
	protected Context c;
	
	private List<Fragment> fragments;
	/**
	 * @param fm
	 * @param fragments
	 */
	public PagerAdapter(FragmentManager fm, List<Fragment> fragments, 
						Context context) 
	{
		super(fm);
		c = context;
		this.fragments = fragments;
	}
	/* (non-Javadoc)
	 * @see android.support.v4.app.FragmentPagerAdapter#getItem(int)
	 */
	@Override
	public Fragment getItem(int position) 
	{
		return this.fragments.get(position);
	}

	/* (non-Javadoc)
	 * @see android.support.v4.view.PagerAdapter#getCount()
	 */
	@Override
	public int getCount() 
	{
		return this.fragments.size();
	}
	
	@Override
	public CharSequence getPageTitle (int position) 
	{
		if(position == 0) {
			return c.getResources().getString(
					R.string.title_activity_manage_transfers);
		}
//		if(position == 0) {
//			//dashboard-home
//			return c.getResources().getString(R.string.title_activity_home);
//		}
//		else if(position == 1) {
//			return c.getResources().getString(R.string.title_activity_manage_transfers);
//		}
//		else if(position == 2) {
//			return c.getResources().getString(R.string.title_activity_start_transfer);
//		}
		else 
		{
			return "untitled";
		}
	}
}
