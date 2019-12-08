package com.servoy.eclipse.ui.tweaks;

import java.net.URL;

import org.eclipse.core.runtime.Plugin;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageDescriptorRelay;
import org.osgi.framework.BundleContext;

public class Activator extends Plugin
{

	/**
	 * The PLUGIN_ID for com.servoy.eclipse.ui.tweaks
	 */
	public static final String PLUGIN_ID = "com.servoy.eclipse.ui.tweaks";

	// The shared instance
	private static Activator plugin;

	@Override
	public void start(BundleContext context) throws Exception
	{
		super.start(context);
		plugin = this;
		ImageDescriptor.loaderRelay = new ImageDescriptorRelay()
		{
			@Override
			public ImageDescriptor createFromURL(URL url)
			{
				try
				{
					return ImageReplacementMapper.getUrlBasedImageReplacement(url, null, null);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
				return null;
			}

			@Override
			public ImageDescriptor createFromFile(Class< ? > location, String filename)
			{
				try
				{
					return ImageReplacementMapper.getFileBasedImageReplacement(location, filename, null, null);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
				return null;
			}
		};
	}

	@Override
	public void stop(BundleContext context) throws Exception
	{
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance.
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault()
	{
		return plugin;
	}

}
