/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */
package com.servoy.eclipse.core;

import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.SwingUtilities;

import net.sourceforge.sqlexplorer.ExplorerException;
import net.sourceforge.sqlexplorer.dbproduct.Alias;
import net.sourceforge.sqlexplorer.dbproduct.AliasManager;
import net.sourceforge.sqlexplorer.dbproduct.ManagedDriver;
import net.sourceforge.sqlexplorer.dbproduct.User;
import net.sourceforge.sqlexplorer.plugin.SQLExplorerPlugin;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.WorkbenchJob;
import org.osgi.framework.BundleContext;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;

import com.servoy.eclipse.core.doc.IDocumentationManagerProvider;
import com.servoy.eclipse.core.resource.PersistEditorInput;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.IDebugClientHandler;
import com.servoy.j2db.IDebugJ2DBClient;
import com.servoy.j2db.IDebugWebClient;
import com.servoy.j2db.IDesignerCallback;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IServerInternal;
import com.servoy.j2db.persistence.IServerManagerInternal;
import com.servoy.j2db.server.shared.ApplicationServerSingleton;
import com.servoy.j2db.server.shared.IDebugHeadlessClient;
import com.servoy.j2db.smart.J2DBClient;
import com.servoy.j2db.util.Settings;


/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends Plugin
{
	private volatile boolean defaultAccessed = false;

	private Boolean sqlExplorerLoaded = null;

	private DesignApplication designClient;

	private IDesignerCallback designerCallback;


	/**
	 * @author jcompagner
	 * 
	 */
	private static final class SQLExplorerAliasCreatorJob extends WorkbenchJob
	{
		/**
		 * @param name
		 */
		private SQLExplorerAliasCreatorJob(String name)
		{
			super(name);
		}

		@Override
		public IStatus runInUIThread(IProgressMonitor monitor)
		{
			IServerManagerInternal serverManager = ServoyModel.getServerManager();

			String[] serverNames = serverManager.getServerNames(true, true, false, false);
			AliasManager aliasManager = SQLExplorerPlugin.getDefault().getAliasManager();

			try
			{
				aliasManager.loadAliases();
			}
			catch (ExplorerException e1)
			{
				ServoyLog.logError(e1);
			}

			for (final String serverName : serverNames)
			{
				IServerInternal server = (IServerInternal)serverManager.getServer(serverName);
				final ManagedDriver driver = new ManagedDriver(serverName);
				try
				{
					driver.setJdbcDriver(serverManager.loadDriver(server.getConfig().getDriver(), server.getServerURL()));
				}
				catch (Exception e)
				{
					ServoyLog.logError(e);
				}
				driver.setName(serverName);
				driver.setUrl(server.getServerURL());
				driver.setDriverClassName(server.getConfig().getDriver());
				Alias alias = new Alias(serverName)
				{
					/**
					 * @see net.sourceforge.sqlexplorer.dbproduct.Alias#getDriver()
					 */
					@Override
					public ManagedDriver getDriver()
					{
						return driver;
					}
				};
				alias.setAutoLogon(true);
				alias.setConnectAtStartup(false);
				String userName = server.getConfig().getUserName();
				if (userName != null && userName.trim().length() != 0)
				{
					User user = new User(userName, server.getConfig().getPassword());
					user.setAutoCommit(false);
					alias.setDefaultUser(user);
				}
				else
				{
					alias.setHasNoUserName(true);
				}
				alias.setUrl(server.getServerURL());
				try
				{
					aliasManager.addAlias(alias);
				}
				catch (ExplorerException e)
				{
					ServoyLog.logError(e);
				}
			}
			try
			{
				aliasManager.saveAliases();
			}
			catch (ExplorerException e)
			{
				ServoyLog.logError(e);
			}
			return Status.OK_STATUS;
		}
	}

	// The plug-in ID
	public static final String PLUGIN_ID = "com.servoy.eclipse.core"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	private IDocumentationManagerProvider docManagerpProvider;

	/**
	 * The constructor
	 */
	public Activator()
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.Plugins#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception
	{
		super.start(context);
		plugin = this;

		IPreferenceStore prefs = PlatformUI.getPreferenceStore();
		prefs.setValue(IWorkbenchPreferenceConstants.SHOW_PROGRESS_ON_STARTUP, true);

		Dictionary<String, String[]> properties = new Hashtable<String, String[]>(1);
		properties.put(URLConstants.URL_HANDLER_PROTOCOL, new String[] { MediaURLStreamHandlerService.PROTOCOL });
		String serviceClass = URLStreamHandlerService.class.getName();
		context.registerService(serviceClass, new MediaURLStreamHandlerService(), properties);

		// start the design client here from within a job.
		// Without this, the main thread may block on the awt thread when the debig SC client is created which causes
		// paint issues in debug SC on the mac.
		new Job("DesignClientStarter")
		{
			@Override
			protected IStatus run(IProgressMonitor monitor)
			{
				getDefault().getDesignClient().getClient();
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	public boolean isSqlExplorerLoaded()
	{
		if (sqlExplorerLoaded == null)
		{
			sqlExplorerLoaded = Boolean.FALSE;
			try
			{
				Class.forName("net.sourceforge.sqlexplorer.plugin.SQLExplorerPlugin", false, getClass().getClassLoader()); //$NON-NLS-1$
				sqlExplorerLoaded = Boolean.TRUE;
				generateSQLExplorerAliasses();
			}
			catch (Exception e)
			{
				// ignore
			}
		}
		return sqlExplorerLoaded.booleanValue();
	}

	/**
	 * 
	 */
	private void generateSQLExplorerAliasses()
	{
		WorkbenchJob job = new SQLExplorerAliasCreatorJob("creating db aliasses"); //$NON-NLS-1$
		job.setSystem(true);
		job.setUser(false);
		job.setRule(ResourcesPlugin.getWorkspace().getRoot());
		job.schedule();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.Plugin#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception
	{
		defaultAccessed = false;
		if (ServoyModelManager.getServoyModelManager().isServoyModelCreated())
		{
			ServoyModelManager.getServoyModelManager().getServoyModel().dispose();
		}

		plugin = null;
		super.stop(context);
		SwingUtilities.invokeAndWait(new Runnable() // wait until webserver is stopped for case of
		// restart (webserver cannot re-start when port is still in use, this may even cause a freeze after restart)
		{
			public void run()
			{
				try
				{
					if (Settings.getInstance() != null)
					{
						IApplication application = getDebugClientHandler().getDebugReadyClient();
						if (application instanceof J2DBClient)
						{
							((J2DBClient)application).shutDown(true);
						}
						Settings.getInstance().save();
					}
					ApplicationServerSingleton.get().shutDown();
				}
				catch (Exception e)
				{
					ServoyLog.logError(e);
				}
			}
		});
	}

	/**
	 * 
	 */
	public IDebugClientHandler getDebugClientHandler()
	{
		IDebugClientHandler dch = ApplicationServerSingleton.get().getDebugClientHandler();
		if (designerCallback == null)
		{
			designerCallback = new IDesignerCallback()
			{
				public void showFormInDesigner(final Form form)
				{
					FlattenedSolution editingSolution = ServoyModelManager.getServoyModelManager().getServoyModel().getActiveProject().getEditingFlattenedSolution();
					Form testForm = editingSolution.getForm(form.getID());
					if (testForm == null) return;
					Display.getDefault().asyncExec(new Runnable()
					{
						public void run()
						{
							if (form != null)
							{
								try
								{
									PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().openEditor(
										new PersistEditorInput(form.getName(), form.getSolution().getName(), form.getUUID()),
										PlatformUI.getWorkbench().getEditorRegistry().getDefaultEditor(null,
											Platform.getContentTypeManager().getContentType(PersistEditorInput.FORM_RESOURCE_ID)).getId());
									PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().forceActive();

								}
								catch (PartInitException ex)
								{
									ServoyLog.logError(ex);
								}
							}
						}
					});
				}
			};
			dch.setDesignerCallback(designerCallback);
		}
		return dch;
	}

	/**
	 * @return the debugJ2DBClient
	 */
	public IDebugJ2DBClient getDebugJ2DBClient()
	{
		return getDebugClientHandler().getDebugSmartClient();
	}

	public IDebugJ2DBClient getJSUnitJ2DBClient()
	{
		return getDebugClientHandler().getJSUnitJ2DBClient();
	}

	/**
	 * @param form
	 */
	public void showInDebugClients(Form form)
	{
		getDebugClientHandler().showInDebugClients(form);
	}

	/**
	 * @return
	 */
	public IDebugWebClient getDebugWebClient()
	{
		return getDebugClientHandler().getDebugWebClient();
	}

	public IDebugHeadlessClient getDebugHeadlessClient()
	{
		return getDebugClientHandler().getDebugHeadlessClient();
	}

	public DesignApplication getDesignClient()
	{
		if (designClient == null)
		{
			designClient = new DesignApplication();
		}
		return designClient;
	}

	/**
	* Returns the shared instance
	* 
	* @return the shared instance
	*/
	public static Activator getDefault()
	{
		if (plugin != null && !plugin.defaultAccessed)
		{
			plugin.defaultAccessed = true;
			plugin.initialize();
		}
		return plugin;
	}

	private void initialize()
	{
		defaultAccessed = true;
		final ServoyModel servoyModel = ServoyModelManager.getServoyModelManager().getServoyModel();

		XMLScriptObjectAdapterLoader.loadDocumentationFromXML();
		MethodTemplatesLoader.loadMethodTemplatesFromXML();


		IActiveProjectListener apl = new IActiveProjectListener()
		{
			public void activeProjectChanged(final ServoyProject project)
			{
				if (designClient != null)
				{
					designClient.refreshI18NMessages();
				}
				// can this be really later? or should we wait?
				// its much nice to do that later in the event thread.
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						IDebugClientHandler dch = getDebugClientHandler();
						if (project != null)
						{
							dch.reloadDebugSolution(project.getSolution());
							dch.reloadDebugSolutionSecurity();
						}
						else
						{
							dch.reloadDebugSolution(null);
						}
					}
				});
			}

			public void activeProjectUpdated(final ServoyProject activeProject, int updateInfo)
			{
				if (activeProject == null) return;
				if (updateInfo == IActiveProjectListener.MODULES_UPDATED)
				{
					// in order to have a good module cache in the flattened solution
					SwingUtilities.invokeLater(new Runnable()
					{
						public void run()
						{
							IDebugClientHandler dch = getDebugClientHandler();
							dch.reloadDebugSolution(activeProject.getSolution());
							dch.reloadDebugSolutionSecurity();
						}
					});
				}
				else if (updateInfo == IActiveProjectListener.RESOURCES_UPDATED_ON_ACTIVE_PROJECT)
				{
					SwingUtilities.invokeLater(new Runnable()
					{
						public void run()
						{
							IDebugClientHandler dch = getDebugClientHandler();
							dch.reloadDebugSolutionSecurity();
							dch.reloadAllStyles();
						}
					});
				}
				else if (updateInfo == IActiveProjectListener.STYLES_ADDED_OR_REMOVED)
				{
					SwingUtilities.invokeLater(new Runnable()
					{
						public void run()
						{
							IDebugClientHandler dch = getDebugClientHandler();
							dch.reloadAllStyles();
						}
					});
				}
				else if (updateInfo == IActiveProjectListener.SECURITY_INFO_CHANGED)
				{
					SwingUtilities.invokeLater(new Runnable()
					{
						public void run()
						{
							IDebugClientHandler dch = getDebugClientHandler();
							dch.reloadDebugSolutionSecurity();
						}
					});
				}
			}

			public boolean activeProjectWillChange(ServoyProject activeProject, ServoyProject toProject)
			{
				return true;
			}
		};
		servoyModel.addActiveProjectListener(apl);
		apl.activeProjectChanged(servoyModel.getActiveProject());

		servoyModel.addPersistChangeListener(true, new IPersistChangeListener()
		{
			public void persistChanges(final Collection<IPersist> changes)
			{
				SwingUtilities.invokeLater(new Runnable()
				{
					public void run()
					{
						IDebugClientHandler dch = getDebugClientHandler();
						dch.refreshDebugClients(changes);
					}
				});
			}
		});
	}

	public synchronized IDocumentationManagerProvider getDocumentationManagerProvider()
	{
		if (docManagerpProvider == null)
		{
			IExtensionRegistry reg = Platform.getExtensionRegistry();
			IExtensionPoint ep = reg.getExtensionPoint(IDocumentationManagerProvider.EXTENSION_ID);
			IExtension[] extensions = ep.getExtensions();

			if (extensions == null || extensions.length == 0)
			{
				ServoyLog.logWarning("Could not find documentation provider server starter plugin (extension point " + //$NON-NLS-1$
					IDocumentationManagerProvider.EXTENSION_ID + ")", null); //$NON-NLS-1$
				return null;
			}
			if (extensions.length > 1)
			{
				ServoyLog.logWarning("Multiple documentation manager plugins found (extension point " + //$NON-NLS-1$
					IDocumentationManagerProvider.EXTENSION_ID + ")", null); //$NON-NLS-1$
			}
			IConfigurationElement[] ce = extensions[0].getConfigurationElements();
			if (ce == null || ce.length == 0)
			{
				ServoyLog.logWarning("Could not read documentation provider plugin (extension point " + IDocumentationManagerProvider.EXTENSION_ID + ")", null); //$NON-NLS-1$ //$NON-NLS-2$
				return null;
			}
			if (ce.length > 1)
			{
				ServoyLog.logWarning("Multiple extensions for documentation manager plugins found (extension point " + //$NON-NLS-1$
					IDocumentationManagerProvider.EXTENSION_ID + ")", null); //$NON-NLS-1$
			}
			try
			{
				docManagerpProvider = (IDocumentationManagerProvider)ce[0].createExecutableExtension("class"); //$NON-NLS-1$
			}
			catch (CoreException e)
			{
				ServoyLog.logWarning("Could not create documentation provider plugin (extension point " + IDocumentationManagerProvider.EXTENSION_ID + ")", e); //$NON-NLS-1$ //$NON-NLS-2$
				return null;
			}
			if (docManagerpProvider == null)
			{
				ServoyLog.logWarning("Could not load documentation provider plugin (extension point " + IDocumentationManagerProvider.EXTENSION_ID + ")", null); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return docManagerpProvider;
	}
}
