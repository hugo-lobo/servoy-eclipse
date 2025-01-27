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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;
import org.apache.wicket.Request;
import org.apache.wicket.Response;
import org.apache.wicket.Session;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PerspectiveAdapter;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.activities.IActivityManager;
import org.eclipse.ui.activities.IWorkbenchActivitySupport;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.cheatsheets.OpenCheatSheetAction;
import org.eclipse.ui.internal.WorkbenchPage;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.cheatsheets.ICheatSheetResource;
import org.eclipse.ui.internal.registry.ActionSetRegistry;
import org.eclipse.ui.internal.registry.IActionSetDescriptor;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import org.sablo.websocket.GetHttpSessionConfigurator;

import com.servoy.base.persistence.constants.IFormConstants;
import com.servoy.eclipse.core.doc.IDocumentationManagerProvider;
import com.servoy.eclipse.core.repository.SwitchableEclipseUserManager;
import com.servoy.eclipse.core.resource.PersistEditorInput;
import com.servoy.eclipse.core.util.RadioButtonsDialog;
import com.servoy.eclipse.core.util.UIUtils;
import com.servoy.eclipse.model.DesignApplication;
import com.servoy.eclipse.model.IPluginBaseClassLoaderProvider;
import com.servoy.eclipse.model.builder.BuilderDependencies;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.eclipse.model.ngpackages.ILoadedNGPackagesListener;
import com.servoy.eclipse.model.repository.EclipseRepositoryFactory;
import com.servoy.eclipse.model.util.ModelUtils;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.j2db.ClientVersion;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.IBeanManager;
import com.servoy.j2db.IBeanManagerInternal;
import com.servoy.j2db.IBrowserLauncher;
import com.servoy.j2db.IDebugClient;
import com.servoy.j2db.IDebugClientHandler;
import com.servoy.j2db.IDebugJ2DBClient;
import com.servoy.j2db.IDebugWebClient;
import com.servoy.j2db.IDesignerCallback;
import com.servoy.j2db.J2DBGlobals;
import com.servoy.j2db.dataprocessing.ClientInfo;
import com.servoy.j2db.dataprocessing.IDataServer;
import com.servoy.j2db.debug.DebugClientHandler;
import com.servoy.j2db.debug.DebugUtils;
import com.servoy.j2db.debug.DebugWebClientSession;
import com.servoy.j2db.debug.RemoteDebugScriptEngine;
import com.servoy.j2db.persistence.Bean;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IFormElement;
import com.servoy.j2db.persistence.IMethodTemplate;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IPersistChangeListener;
import com.servoy.j2db.persistence.MethodTemplate;
import com.servoy.j2db.persistence.MethodTemplatesFactory;
import com.servoy.j2db.plugins.IMethodTemplatesProvider;
import com.servoy.j2db.plugins.PluginManager;
import com.servoy.j2db.scripting.InstanceJavaMembers;
import com.servoy.j2db.server.ngclient.BodyPortal;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IApplicationServerSingleton;
import com.servoy.j2db.server.shared.IDebugHeadlessClient;
import com.servoy.j2db.server.shared.IUserManager;
import com.servoy.j2db.server.shared.IUserManagerFactory;
import com.servoy.j2db.server.shared.IWebClientSessionFactory;
import com.servoy.j2db.server.starter.IServerStarter;
import com.servoy.j2db.util.CompositeIterable;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.IDeveloperURLStreamHandler;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;


/**
 * The activator class controls the plug-in life cycle, test
 */
public class Activator extends Plugin
{
	public static final String RECREATE_ON_I18N_CHANGE_PREFERENCE = "recreate.forms.on.i18n.change";

	public static final String AUTO_CREATE_I18N_FILES_PREFERENCE = "auto.create.i18n.files";

	public static final String PROPERTIES_FILE_PATH_SETTING = "servoyPropertiesFile";

	// The plug-in ID
	public static final String PLUGIN_ID = "com.servoy.eclipse.core";

	public static final QualifiedName DESIGN_PAGE_TYPE_KEY = new QualifiedName(PLUGIN_ID, "designPagetype");

	// The shared instance
	private static Activator plugin;

	private IDocumentationManagerProvider docManagerpProvider;

	private IDebuggerStarter debuggerStarter;

	private IServerStarter ss;

	private volatile boolean defaultAccessed = false;

	private IDesignerCallback designerCallback;

	private volatile boolean isPostgresInstallChecked = false;

	private final List<Runnable> postgressCheckedNotify = new ArrayList<Runnable>(3);


	@Override
	public void start(BundleContext context) throws Exception
	{
		ModelUtils.assertUINotDisabled(PLUGIN_ID);

		super.start(context);
		plugin = this;

		String propertiesFile = InstanceScope.INSTANCE.getNode(PLUGIN_ID).get(PROPERTIES_FILE_PATH_SETTING, null);
		if (!Utils.stringIsEmpty(propertiesFile) && System.getProperty("property-file", null) == null)
		{
			System.setProperty("property-file", propertiesFile);
		}

		ServiceReference ref = context.getServiceReference(IServerStarter.class);
		ss = (IServerStarter)context.getService(ref);
		if (ss == null)
		{
			throw new IllegalStateException("Could not load application server plugin");
		}
		// now start the app server in the back ground.
		new Thread(() -> startAppServer()).start();

		IExtensionRegistry reg = Platform.getExtensionRegistry();
		IExtensionPoint ep = reg.getExtensionPoint(IPluginBaseClassLoaderProvider.EXTENSION_ID);
		IExtension[] extensions = ep.getExtensions();
		if (extensions != null && extensions.length > 0)
		{
			for (IExtension extension : extensions)
			{
				IPluginBaseClassLoaderProvider provider = (IPluginBaseClassLoaderProvider)extension.getConfigurationElements()[0].createExecutableExtension(
					"class");
				ss.setBaseClassloader(provider.getClassLoader());
				break; //we support only one
			}
		}
		else
		{
			throw new IllegalStateException("Could not load plugin base classloader provider");
		}

		IPreferenceStore prefs = PlatformUI.getPreferenceStore();
		prefs.setValue(IWorkbenchPreferenceConstants.SHOW_PROGRESS_ON_STARTUP, true);

		Dictionary<String, String[]> properties = new Hashtable<String, String[]>(1);
		properties.put(URLConstants.URL_HANDLER_PROTOCOL, new String[] { MediaURLStreamHandlerService.PROTOCOL });
		String serviceClass = URLStreamHandlerService.class.getName();
		context.registerService(serviceClass, new MediaURLStreamHandlerService(), properties);

		// listen for workbench shutdown - make sure that any DLTK JS running process is stopped when this happens cause
		// otherwise the developer might hang on shutdown. For example if you are debuggin a smart-client, a breakpoint
		// is hit (which blocks AWT thread) then you open some forms in form designer (they don't paint, waiting for
		// AWT to be able to paint things, but do secondary SWT event loops to keep developer responsive) then you close
		// developer => AWT waiting in breakpoint, SWT waiting in secondary event loops for AWT although the platform is
		// closing, platform close is waiting for root SWT worker to finish => hang
		PlatformUI.getWorkbench().addWorkbenchListener(new IWorkbenchListener()
		{

			@Override
			public boolean preShutdown(IWorkbench workbench, boolean forced)
			{
				// allow shutdown
				return true;
			}

			@Override
			public void postShutdown(IWorkbench workbench)
			{
				// workbench shutdown started; close running JS debug sessions;
				// we can get lots of exceptions in the log because a big chunk of SWT thread
				// pending operations can be released by this and continue running but
				// the Display/widgets are already be disposed
				stopAnyJSDebugSessionThatSuspendsAWT();
			}
		});

		// We need to hook a listener and detect when the Welcome page is closed.
		// (And for that we need to hook another listener to detect when the workbench window is opened).
		PlatformUI.getWorkbench().addWindowListener(new IWindowListener()
		{
			public void windowActivated(IWorkbenchWindow window)
			{
			}

			public void windowClosed(IWorkbenchWindow window)
			{
			}

			public void windowDeactivated(IWorkbenchWindow window)
			{
			}

			public void windowOpened(IWorkbenchWindow window)
			{
				/* Remove redundant activities to reduce UI clutter. */
				String[] activityIds = { "com.servoy.eclipse.activities.javaDevelopment", "org.eclipse.team.cvs", "org.eclipse.antDevelopment", "org.eclipse.javaDevelopment", "org.eclipse.plugInDevelopment", "com.servoy.eclipse.activities.html", "com.servoy.eclipse.activities.xml", "com.servoy.eclipse.activities.dltk", "com.servoy.eclipse.activities.edit", "org.eclipse.equinox.p2.ui.sdk.classicUpdate" };
				IWorkbenchActivitySupport was = PlatformUI.getWorkbench().getActivitySupport();
				IActivityManager wasAM = was.getActivityManager();
				List<String> activitiesToDisable = Arrays.asList(activityIds);
				Set<String> keepEnabled = new HashSet<String>();
				for (Object o : wasAM.getDefinedActivityIds())
				{
					String id = (String)o;
					if (!activitiesToDisable.contains(id)) keepEnabled.add(id);
				}
				was.setEnabledActivityIds(keepEnabled);

				/* Remove the run/debug actions */
				String[] actionIds = { "org.eclipse.debug.ui.launchActionSet" };
				ActionSetRegistry reg = WorkbenchPlugin.getDefault().getActionSetRegistry();
				IActionSetDescriptor[] actionSets = reg.getActionSets();
				for (IActionSetDescriptor element : actionSets)
				{
					for (String actionSetId : actionIds)
					{
						if (Utils.stringSafeEquals(element.getId(), actionSetId))
						{
							IExtension ext = element.getConfigurationElement().getDeclaringExtension();
							reg.removeExtension(ext, new Object[] { element });
						}
					}
				}

				/* Hide the External Tools set */
				final IEclipsePreferences eclipsePref = getEclipsePreferences();
				final Preferences node = eclipsePref.node("activatedPerspectives"); //the activated perspectives will be stored in this node
				final IWorkbenchWindow workbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

				WorkbenchPage workbenchPage = (WorkbenchPage)workbenchWindow.getActivePage();

				//remove ExternalToolsSet from current perspective - if a restart occurs, the action set has to remain removed
				IPerspectiveDescriptor perspectiveDescriptor = workbenchPage.getPerspective();
				if (perspectiveDescriptor != null && node.getBoolean(perspectiveDescriptor.getId(), true))
				{
					turnOffExternalToolsActionSet(workbenchWindow, perspectiveDescriptor, node);
				}

				//add perspective activated listener to remove External Tools set from any activated perspective
				workbenchWindow.addPerspectiveListener(new PerspectiveAdapter()
				{
					@Override
					public void perspectiveActivated(IWorkbenchPage page, IPerspectiveDescriptor perspectiveDescriptor)
					{
						if (perspectiveDescriptor != null && node.getBoolean(perspectiveDescriptor.getId(), true))
						{
							super.perspectiveActivated(page, perspectiveDescriptor);
							turnOffExternalToolsActionSet(workbenchWindow, perspectiveDescriptor, node);
						}
					}
				});

				if (!ss.getApplicationServer().hasDeveloperLicense() ||
					Utils.getAsBoolean(Settings.getInstance().getProperty("servoy.developer.showStartPage", "true")))
				{
					PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
					{
						@Override
						public void run()
						{
							IWorkbenchWindow active = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
							if (active != null)
							{
								IWorkbenchPage page = active.getActivePage();
								if (page != null)
								{
									IEditorReference[] editorReferences = page.getEditorReferences();
									for (IEditorReference editorReference : editorReferences)
									{
										if (editorReference.getEditor(false) instanceof MainConceptsPageBrowserEditor ||
											MainConceptsPageBrowserEditorInput.NAME.equals(editorReference.getPartName()))
										{
											// for some reason eclipse saved the start page editor, close it then
											page.closeEditors(new IEditorReference[] { editorReference }, false);
										}
									}
								}
							}
							/*
							 * for now disable the main concepts page, we first need to have content.
							 */
							/*
							 * try { if (eclipsePref.getBoolean("firstRun", true)) {
							 * PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().openEditor(MainConceptsPageBrowserEditor.INPUT,
							 * MainConceptsPageBrowserEditor.MAINCONCEPTSPAGE_BROWSER_EDITOR_ID); eclipsePref.putBoolean("firstRun", false); } } catch
							 * (Exception e) { ServoyLog.logError("Failed to open browser editor.", e); }
							 */
						}
					});
				}
			}
		});

		// turn of auto building, don't touch servoy model before IServerStarter because that needs to be first initialized.
		IWorkspaceDescription description = ResourcesPlugin.getWorkspace().getDescription();
		boolean autoBuilding = description.isAutoBuilding();
		if (autoBuilding)
		{
			description.setAutoBuilding(false);
			ResourcesPlugin.getWorkspace().setDescription(description);
			ServoyModelManager.getServoyModelManager().addDoneListener(() -> {
				IWorkspaceDescription des = ResourcesPlugin.getWorkspace().getDescription();
				des.setAutoBuilding(true);
				try
				{
					ResourcesPlugin.getWorkspace().setDescription(des);
				}
				catch (CoreException e)
				{
					ServoyLog.logError(e);
				}
			});
		}

	}

	private void turnOffExternalToolsActionSet(IWorkbenchWindow workbenchWindow, IPerspectiveDescriptor perspectiveDescriptor, Preferences node)
	{
		if (workbenchWindow.getActivePage() instanceof WorkbenchPage)
		{
			WorkbenchPage worbenchPage = (WorkbenchPage)workbenchWindow.getActivePage();
			ActionSetRegistry reg = WorkbenchPlugin.getDefault().getActionSetRegistry();
			IActionSetDescriptor[] actionSets = reg.getActionSets();
			for (IActionSetDescriptor actionSetDescriptor : actionSets)
			{
				if (actionSetDescriptor.getId().indexOf("org.eclipse.ui.externaltools.ExternalToolsSet") > -1)
				{
					worbenchPage.hideActionSet(actionSetDescriptor.getId());
					break;
				}
			}
		}
		node.putBoolean(perspectiveDescriptor.getId(), false);
		try
		{
			node.flush();
		}
		catch (BackingStoreException e)
		{
			ServoyLog.logError("Failed to persist changes.", e);
		}
	}

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
		stopAnyJSDebugSessionThatSuspendsAWT();

		// shutdown all non-swing clients; no need to run this in AWT EDT
		try
		{
			List<IDebugClient> nonSwingApps = getDebugClientHandler().getActiveDebugClients();
			for (IDebugClient application : nonSwingApps)
			{
				ClientInfo ci = application.getClientInfo();
				if (ci != null && !Utils.isSwingClient(ci.getApplicationType())) application.shutDown(true);
			}
		}
		catch (Exception e)
		{
			ServoyLog.logError(e);
		}

		// shutdown swing clients
		boolean interrupted = false;
		try
		{
			Runnable run = new Runnable()
			{
				public void run()
				{
					try
					{
						List<IDebugClient> swingApps = getDebugClientHandler().getActiveDebugClients();
						for (IDebugClient application : swingApps)
						{
							ClientInfo ci = application.getClientInfo();
							if (ci != null && Utils.isSwingClient(ci.getApplicationType())) application.shutDown(true);
						}
					}
					catch (Exception e)
					{
						ServoyLog.logError(e);
					}
				}
			};
			if (Utils.isAppleMacOS())
			{
				boolean allDisplaysDisposed = true;
				// in mac os 10.8 with java 1.6_43 the display is disposed before the clients are closed
				//  -- test first if we are in that specific case
				//loop through all the threads to see if there is still a display associated with any of them
				// if there is a display associated
				Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
				for (Thread thread : threadSet)
				{
					if (Display.findDisplay(thread) != null)
					{
						allDisplaysDisposed = false;
						DebugUtils.invokeAndWaitWhileDispatchingOnSWT(run);
						break;
					}
				}
				// -- display disposed case
				if (allDisplaysDisposed)
				{ // create a display
					try
					{
						Display d = Display.getDefault();
						DebugUtils.invokeAndWaitWhileDispatchingOnSWT(run);
						d.dispose();
					}
					catch (Exception ex)
					{
						// couldn't create a display, everything is already destroyed
						// just don't do anything for now, touching swing will hang developer on mac
					}
				}
			}
			else
			{
				SwingUtilities.invokeAndWait(run);
			}


		}
		catch (Exception e)
		{
			ServoyLog.logWarning("Interrupted while waiting for clients to shut down on stop. Continuing with server shutdown.", null);
			interrupted = true;
		}

		Settings.getInstance().save();

		// wait until webserver is stopped for case of
		// restart (webserver cannot re-start when port is still in use, this may even cause a freeze after restart)
		IApplicationServerSingleton appServer = ApplicationServerRegistry.get();
		if (appServer != null)
		{
			appServer.shutDown();
			appServer.doNativeShutdown();
			J2DBGlobals.setSingletonServiceProvider(null); // avoid a null pointer exception that can happen when DLTK stops the debugger after appserver singleton gets cleared (due to a Context.enter() call)
			J2DBGlobals.setServiceProvider(null);
			ApplicationServerRegistry.destroy();
		}
		super.stop(context);

		if (interrupted) Thread.interrupted(); // someone is in a hurry, let callers know about that
	}

	protected void stopAnyJSDebugSessionThatSuspendsAWT()
	{
		IApplication debugReadyClient = getDebugClientHandler().getDebugReadyClient();
		if (debugReadyClient != null && debugReadyClient.getScriptEngine() instanceof RemoteDebugScriptEngine &&
			((RemoteDebugScriptEngine)debugReadyClient.getScriptEngine()).isAWTSuspendedRunningScript())
		{
			((RemoteDebugScriptEngine)debugReadyClient.getScriptEngine()).getDebugger().close();
		}
	}

	/**
	 *
	 */
	public IDebugClientHandler getDebugClientHandler()
	{
		IDebugClientHandler dch = ApplicationServerRegistry.get().getDebugClientHandler();
		if (designerCallback == null)
		{
			designerCallback = new IDesignerCallback()
			{
				public void showFormInDesigner(Form form)
				{
					FlattenedSolution editingSolution = ServoyModelManager.getServoyModelManager()
						.getServoyModel()
						.getActiveProject()
						.getEditingFlattenedSolution();
					final Form testForm = editingSolution.getForm(form.getName());
					if (testForm == null) return;
					Display.getDefault().asyncExec(new Runnable()
					{
						public void run()
						{
							try
							{
								PlatformUI.getWorkbench()
									.getActiveWorkbenchWindow()
									.getActivePage()
									.openEditor(
										PersistEditorInput.createFormEditorInput(testForm), PlatformUI.getWorkbench()
											.getEditorRegistry()
											.getDefaultEditor(null,
												Platform.getContentTypeManager().getContentType(PersistEditorInput.FORM_RESOURCE_ID))
											.getId());
								PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell().forceActive();

							}
							catch (PartInitException ex)
							{
								ServoyLog.logError(ex);
							}
						}
					});
				}

				public void addScriptObjects(IDebugClient client, Scriptable scope)
				{
					Context.enter();
					try
					{
						scope.put("servoyDeveloper", scope,
							new NativeJavaObject(scope, new JSDeveloperSolutionModel(client), new InstanceJavaMembers(scope, JSDeveloperSolutionModel.class)));
					}
					finally
					{
						Context.exit();
					}
				}

				/*
				 * (non-Javadoc)
				 *
				 * @see com.servoy.j2db.IDesignerCallback#testAndStartDebugger()
				 */
				public void testAndStartDebugger()
				{
					IDebuggerStarter starter = getDebuggerStarter();
					if (starter != null)
					{
						starter.testAndStartDebugger();
					}
				}

				private final HashMap<String, DeveloperURLStreamHandlerService> urlStreamServices = new HashMap<String, DeveloperURLStreamHandlerService>();

				@Override
				public void addURLStreamHandler(String protocolName, IDeveloperURLStreamHandler handler)
				{
					DeveloperURLStreamHandlerService service = urlStreamServices.get(protocolName);
					if (service == null)
					{
						Dictionary<String, String[]> properties = new Hashtable<String, String[]>(1);
						properties.put(URLConstants.URL_HANDLER_PROTOCOL, new String[] { protocolName });
						String serviceClass = URLStreamHandlerService.class.getName();
						service = new DeveloperURLStreamHandlerService(handler);
						getBundle().getBundleContext().registerService(serviceClass, service, properties);
						urlStreamServices.put(protocolName, service);
					}
					else
					{
						service.setHandler(handler);
					}
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
		IDebugJ2DBClient debugSmartClient = getDebugClientHandler().getDebugSmartClient();
		if (debugSmartClient.getBrowserLauncher() == null)
		{
			debugSmartClient.setBrowserLauncher(new IBrowserLauncher()
			{
				public boolean showURL(String url)
				{
					try
					{
						IWorkbenchBrowserSupport support = PlatformUI.getWorkbench().getBrowserSupport();
						IWebBrowser browser = support.getExternalBrowser();
						browser.openURL(new URL(url));
						return true;
					}
					catch (Exception ex)
					{
						ServoyLog.logError(ex);
						return false;
					}
				}
			});
		}
		return debugSmartClient;
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

	public IDebugClient getDebugNGClient()
	{
		return getDebugClientHandler().getDebugNGClient();
	}

	public IDebugHeadlessClient getDebugHeadlessClient()
	{
		return getDebugClientHandler().getDebugHeadlessClient();
	}

	public DesignApplication getDesignClient()
	{
		return com.servoy.eclipse.model.Activator.getDefault().getDesignClient();
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
			plugin.initialize();
			plugin.defaultAccessed = true;
		}
		return plugin;
	}

	public static BundleContext getBundleContext()
	{
		if (plugin != null)
		{
			return plugin.getBundle().getBundleContext();
		}
		return null;
	}

	/**
	 * Global (workspace) preferences
	 */
	public static IEclipsePreferences getEclipsePreferences()
	{
		return InstanceScope.INSTANCE.getNode(PLUGIN_ID);
	}

	@SuppressWarnings("restriction")
	private void initialize()
	{
		defaultAccessed = true;

		// install servoy model listeners in separate job, when ServoyModel is created in bundle.activator thread
		// a deadlock may occur (display thread waits for loading of ui bundle which waits for core bundle
		// which waits for ServoyModel latch, but the ServoyModel runnable is never running because display thread is blocking in wait)
		new Job("HookupToServoyModel")
		{
			@Override
			protected IStatus run(IProgressMonitor monitor)
			{
				final IDeveloperServoyModel servoyModel = ServoyModelManager.getServoyModelManager().getServoyModel();

				IActiveProjectListener apl = new IActiveProjectListener()
				{
					public void activeProjectChanged(final ServoyProject project)
					{
						BuilderDependencies.getInstance().clear();
						if (getDesignClient() != null)
						{
							getDesignClient().refreshI18NMessages();
						}
						// can this be really later? or should we wait?
						// its much nice to do that later in the event thread.
						UIUtils.invokeLaterOnAWT(new Runnable()
						{
							public void run()
							{
								IDebugClientHandler dch = getDebugClientHandler();
								if (project != null)
								{
									dch.setSolution(project.getSolution());
								}
								else
								{
									dch.setSolution(null);
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
							UIUtils.invokeLaterOnAWT(new Runnable()
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
							UIUtils.invokeLaterOnAWT(new Runnable()
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
							UIUtils.invokeLaterOnAWT(new Runnable()
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
							UIUtils.invokeLaterOnAWT(new Runnable()
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
						UIUtils.invokeLaterOnAWT(new Runnable()
						{
							public void run()
							{
								Collection<IPersist> affectedFormElements = new ArrayList<IPersist>(changes);
								if (changes != null)
								{
									for (IPersist persist : changes)
									{
										if (persist instanceof IFormElement)
										{
											IPersist parent = persist.getParent();
											while (parent instanceof IFormElement)
											{
												if (!affectedFormElements.contains(parent))
												{
													affectedFormElements.add(parent);
												}
												parent = parent.getParent();
											}
											if (parent instanceof Form && (((Form)parent).getView() == IFormConstants.VIEW_TYPE_TABLE ||
												((Form)parent).getView() == IFormConstants.VIEW_TYPE_TABLE_LOCKED ||
												((Form)parent).getView() == IFormConstants.VIEW_TYPE_LIST ||
												((Form)parent).getView() == IFormConstants.VIEW_TYPE_LIST_LOCKED))
											{
												BodyPortal hiddenPortal = new BodyPortal((Form)parent);
												if (!affectedFormElements.contains(hiddenPortal))
												{
													affectedFormElements.add(hiddenPortal);
												}
											}
										}
									}
								}
								IDebugClientHandler dch = getDebugClientHandler();
								dch.refreshDebugClients(changes);
							}
						});
					}
				});

				// flush bean design instances of changed beans
				servoyModel.addPersistChangeListener(false, new IPersistChangeListener()
				{
					public void persistChanges(final Collection<IPersist> changes)
					{
						for (IPersist persist : changes)
						{
							if (persist instanceof Bean)
							{
								FlattenedSolution editingFlattenedSolution = ModelUtils.getEditingFlattenedSolution(persist);
								if (editingFlattenedSolution != null)
								{
									editingFlattenedSolution.flushBeanDesignInstance((Bean)persist);
								}
							}
						}
					}
				});

				servoyModel.addI18NChangeListener(new I18NChangeListener()
				{
					public void i18nChanged()
					{
						servoyModel.getMessagesManager().removeCachedMessages();
						UIUtils.invokeLaterOnAWT(new Runnable()
						{
							public void run()
							{
								IDebugClientHandler dch = getDebugClientHandler();
								dch.refreshDebugClientsI18N(getEclipsePreferences().getBoolean(RECREATE_ON_I18N_CHANGE_PREFERENCE, true));
							}
						});
					}
				});

				servoyModel.getNGPackageManager().addLoadedNGPackagesListener(new ILoadedNGPackagesListener()
				{

					@Override
					public void ngPackagesChanged(CHANGE_REASON changeReason, boolean loadedPackagesAreTheSameAlthoughReferencingModulesChanged)
					{
						if (changeReason != CHANGE_REASON.ACTIVE_PROJECT_CHANGED)
						{
							IDebugClientHandler dch = getDebugClientHandler();
							if (dch.getDebugNGClient() != null)
							{
								dch.getDebugNGClient().recreateForms();
							}
						}
					}
				});
				return Status.OK_STATUS;
			}
		}.schedule();

		Runnable loadDocs = () -> {
			XMLDesignDocsLoader.loadDocumentationFromXML();
			XMLScriptObjectAdapterLoader.loadCoreDocumentationFromXML();
			MethodTemplatesLoader.loadMethodTemplatesFromXML();

			// Try to load documentation XML from plugin and bean jars.
			PluginManager pluginManager = (PluginManager)getDesignClient().getPluginManager();
			IDocumentationManagerProvider documentationManagerProvider = Activator.getDefault().getDocumentationManagerProvider();
			if (documentationManagerProvider != null)
			{
				XMLScriptObjectAdapterLoader.loadDocumentationForPlugins(pluginManager, documentationManagerProvider);
				IBeanManager beanManager = getDesignClient().getBeanManager();
				if (beanManager instanceof IBeanManagerInternal)
				{
					XMLScriptObjectAdapterLoader.loadDocumentationForBeans((IBeanManagerInternal)beanManager, documentationManagerProvider);
				}
			}

			// Visit all column validators/converters and let them add any method templates to
			// MethodTemplate.
			for (Object conv : new CompositeIterable<Object>(//
				pluginManager.getColumnConverterManager().getConverters().values(), //
				pluginManager.getUIConverterManager().getConverters().values(), //
				pluginManager.getColumnValidatorManager().getValidators().values()))
			{
				if (conv instanceof IMethodTemplatesProvider)
				{
					processMethodTemplates(((IMethodTemplatesProvider)conv).getMethodTemplates(MethodTemplatesFactory.getInstance()));
				}
			}
		};
		// apple can't load this on back ground because all kind swing classes are created to get the docs then we could get into a deadlock
		if (Utils.isAppleMacOS())
		{
			loadDocs.run();
		}
		else new Thread(loadDocs).start();
	}

	private void processMethodTemplates(Map<String, IMethodTemplate> templs)
	{
		if (templs != null)
		{
			for (String key : templs.keySet())
			{
				IMethodTemplate src = templs.get(key);
				MethodTemplate.COMMON_TEMPLATES.put(key, new MethodTemplate(src));
			}
		}
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
				ServoyLog.logWarning(
					"Could not find documentation provider server starter plugin (extension point " + IDocumentationManagerProvider.EXTENSION_ID + ")", null);
				return null;
			}
			if (extensions.length > 1)
			{
				ServoyLog.logWarning("Multiple documentation manager plugins found (extension point " + IDocumentationManagerProvider.EXTENSION_ID + ")", null);
			}
			IConfigurationElement[] ce = extensions[0].getConfigurationElements();
			if (ce == null || ce.length == 0)
			{
				ServoyLog.logWarning("Could not read documentation provider plugin (extension point " + IDocumentationManagerProvider.EXTENSION_ID + ")", null);
				return null;
			}
			if (ce.length > 1)
			{
				ServoyLog.logWarning(
					"Multiple extensions for documentation manager plugins found (extension point " + IDocumentationManagerProvider.EXTENSION_ID + ")", null);
			}
			try
			{
				docManagerpProvider = (IDocumentationManagerProvider)ce[0].createExecutableExtension("class");
			}
			catch (CoreException e)
			{
				ServoyLog.logWarning("Could not create documentation provider plugin (extension point " + IDocumentationManagerProvider.EXTENSION_ID + ")", e);
				return null;
			}
			if (docManagerpProvider == null)
			{
				ServoyLog.logWarning("Could not load documentation provider plugin (extension point " + IDocumentationManagerProvider.EXTENSION_ID + ")", null);
			}
		}
		return docManagerpProvider;
	}

	public synchronized IDebuggerStarter getDebuggerStarter()
	{
		if (debuggerStarter == null)
		{
			debuggerStarter = (IDebuggerStarter)createExtensionPoint(IDebuggerStarter.EXTENSION_ID);
		}
		return debuggerStarter;
	}

	/**
	 *
	 */
	public Object createExtensionPoint(String extensionId)
	{
		IExtensionRegistry reg = Platform.getExtensionRegistry();
		IExtensionPoint ep = reg.getExtensionPoint(extensionId);
		IExtension[] extensions = ep.getExtensions();

		if (extensions == null || extensions.length == 0)
		{
			ServoyLog.logWarning("Could not find extension point " + extensionId, null);
			return null;
		}
		if (extensions.length > 1)
		{
			ServoyLog.logWarning("Multiple extensions found for " + extensionId, null);
		}
		IConfigurationElement[] ce = extensions[0].getConfigurationElements();
		if (ce == null || ce.length == 0)
		{
			ServoyLog.logWarning("Could not read  extension point " + extensionId, null);
			return null;
		}
		if (ce.length > 1)
		{
			ServoyLog.logWarning("Multiple extensions found for extension point " + extensionId, null);
		}
		Object extension = null;
		try
		{
			extension = ce[0].createExecutableExtension("class");
		}
		catch (CoreException e)
		{
			ServoyLog.logWarning("Could not create debug starter (extension point " + extensionId + ")", e);
			return null;
		}
		if (extension == null)
		{
			ServoyLog.logWarning("Could not load debug starter (extension point " + extensionId + ")", null);
		}
		return extension;
	}

	private void showFirstCheatSheet()
	{
		final String cheatSheetId = "com.servoy.eclipse.ui.cheatsheet.firstcontact";
		Display.getDefault().syncExec(new Runnable()
		{
			public void run()
			{
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				if (page != null)
				{
					for (IViewReference vw : page.getViewReferences())
						page.setPartState(vw, IWorkbenchPage.STATE_MINIMIZED);

					new OpenCheatSheetAction(cheatSheetId).run();

					// Make the cheat sheet view not-maximized, so that it does not fill up the entire window.
					IViewReference vw = page.findViewReference(ICheatSheetResource.CHEAT_SHEET_VIEW_ID);
					if (vw != null) page.setPartState(vw, IWorkbenchPage.STATE_RESTORED);
				}
			}
		});
	}

	private void startAppServer()
	{
		try
		{
			ss.nativeStartup();
			GetHttpSessionConfigurator.setOriginCheck(GetHttpSessionConfigurator.DISABLE_ORIGIN_CHECK); // securityFiter is not configured in Developer
			ss.setDeveloperStartup(true);
			ss.init();
			ss.setRepositoryFactory(new EclipseRepositoryFactory());
			ss.setDebugClientHandler(new DebugClientHandler());
			ss.setUserManagerFactory(new IUserManagerFactory()
			{
				public IUserManager createUserManager(IDataServer dataServer)
				{
					return new SwitchableEclipseUserManager();
				}
			});
			ss.setWebClientSessionFactory(new IWebClientSessionFactory()
			{
				public Session newSession(Request request, Response response)
				{
					return new DebugWebClientSession(request);
				}
			});
			ss.start(false);
			ss.startWebServer();

			checkApplicationServerVersion(ss.getApplicationServer());
			checkDefaultPostgressInstall(ss.getApplicationServer());

			// set the START_AS_TEAMPROVIDER_SETTING flag as system property, so
			// our team plugin can use it in popupMenu enablement
			System.setProperty(Settings.START_AS_TEAMPROVIDER_SETTING,
				Settings.getInstance().getProperty(Settings.START_AS_TEAMPROVIDER_SETTING, String.valueOf(Settings.START_AS_TEAMPROVIDER_DEFAULT)));
		}
		catch (Exception ex)
		{
			ServoyLog.logError("Failed to start the appserver", ex);
		}
	}

	/**
	 * @param iApplicationServerSingleton
	 */
	private void checkDefaultPostgressInstall(IApplicationServerSingleton appServer)
	{
		File file = new File(appServer.getServoyApplicationServerDirectory() + "/postgres_db/servoy_repository.dbdump");
		if (file.exists())
		{
			// if the zip is there then always aks the question.
			Display.getDefault().asyncExec(new Runnable()
			{
				public void run()
				{
					Shell activeShell = UIUtils.getActiveShell();
					final RadioButtonsDialog installPostgreSQLDialog = new RadioButtonsDialog(activeShell,
						Arrays.asList("Install database (required for first use and tutorials)." +
							" This will install PostgreSQL in the same folder as Servoy.",
							"Install database without sample data.", "Don't install database at all." +
								" You must have a database running to use Servoy.",
							"Ask me on the next start."),
						"Default database not installed.");
					int open = installPostgreSQLDialog.open();

					if (open == 0)
					{
						// create database with sample
						ProgressMonitorDialog dialog = new ProgressMonitorDialog(activeShell);
						try
						{
							String installFile = "install_postgres.sh";
							if (Utils.getPlatform() == Utils.PLATFORM_WINDOWS)
							{
								installFile = "install_postgres.bat";
							}
							dialog.run(true, false, new CreatedDatabaseJob(new File(file.getParentFile(), installFile), // TODO batch file should be sh for none windows...
								new String[] { "repository_server", "bug_db", "example_data", "log_data", "udm", "pdf_forms", "user_data" }, appServer));
						}
						catch (InvocationTargetException | InterruptedException e)
						{
							ServoyLog.logError(e);
						}
					}
					else if (open == 1)
					{
						// create database with just repo
						ProgressMonitorDialog dialog = new ProgressMonitorDialog(activeShell);
						try
						{
							String installFile = "install_postgres_no_samples.sh";
							if (Utils.getPlatform() == Utils.PLATFORM_WINDOWS)
							{
								installFile = "install_postgres_no_samples.bat";
							}
							dialog.run(true, false,
								new CreatedDatabaseJob(new File(file.getParentFile(), installFile), new String[] { "repository_server" }, appServer)); // TODO batch file should be sh for none windows...
						}
						catch (InvocationTargetException | InterruptedException e)
						{
							ServoyLog.logError(e);
						}
					}
					else if (open == 2)
					{
						// delete dump files
						File[] dumpFiles = file.getParentFile().listFiles(path -> path.isFile());
						for (File dumpFile : dumpFiles)
						{
							dumpFile.delete();
						}
						setPostgresChecked();
					}
					else
					{
						setPostgresChecked();
					}
					// 3 ask the same again later.
				}
			});
		}
		else
		{
			setPostgresChecked();
		}
	}

	void setPostgresChecked()
	{
		isPostgresInstallChecked = true;
		Display.getDefault().asyncExec(new Runnable()
		{
			public void run()
			{
				postgressCheckedNotify.stream().forEach(runnable -> runnable.run());
				postgressCheckedNotify.clear();
			}
		});
	}

	/**
	 * Will call the runnable when postgresql check is done, can be immediate
	 * @param toNotify
	 */
	public void addPostgressCheckedCallback(Runnable toNotify)
	{
		if (isPostgresInstallChecked) toNotify.run();
		else this.postgressCheckedNotify.add(toNotify);
	}

	private int updateAppServerFromSerclipse(java.io.File parentFile, int version, int releaseNumber, boolean lts, ActionListener listener) throws Exception
	{
		File file = new File(ApplicationServerRegistry.get().getServoyApplicationServerDirectory() + "/../servoy_updater.jar");
		try (InputStream is = Activator.class.getResourceAsStream("updater/servoy_updater.jar"))
		{
			FileUtils.copyInputStreamToFile(is, file);
		}
		URLClassLoader loader = URLClassLoader.newInstance(new URL[] { file.toURI().toURL() });
		Class< ? > versionCheckClass = loader.loadClass("com.servoy.updater.VersionCheck");
		Method updateAppServerFromSerclipse = versionCheckClass.getMethod("updateAppServerFromSerclipse",
			new Class[] { java.io.File.class, int.class, int.class, boolean.class, ActionListener.class });
		return Utils.getAsInteger(updateAppServerFromSerclipse.invoke(null,
			new Object[] { parentFile, Integer.valueOf(version), Integer.valueOf(releaseNumber), Boolean.valueOf(lts), listener }));
	}

	private void checkApplicationServerVersion(IApplicationServerSingleton applicationServer)
	{
		// check the app server dir
		final String appServerDir = applicationServer.getServoyApplicationServerDirectory();
		File j2dbLib = new File(appServerDir, "lib/j2db.jar");

		if (!j2dbLib.exists())
		{
			Display.getDefault().asyncExec(new Runnable()
			{
				public void run()
				{
					MessageDialog.openError(Display.getDefault().getActiveShell(), "No Servoy ApplicationServer found!",
						"No application server found at: " + appServerDir + "\nPlease make sure that you installed Servoy Developer correctly");
				}
			});
		}
		else
		{
			try
			{
				URLClassLoader classLoader = new URLClassLoader(new URL[] { j2dbLib.toURL() }, ClassLoader.getSystemClassLoader());
				Class< ? > loadClass = classLoader.loadClass("com.servoy.j2db.ClientVersion");
				Method method = loadClass.getMethod("getReleaseNumber", new Class[0]);
				Object o = method.invoke(null, new Object[0]);
				if (o instanceof Integer)
				{
					final int version = ((Integer)o).intValue();
					if (version > ClientVersion.getReleaseNumber())
					{
						Display.getDefault().asyncExec(new Runnable()
						{
							public void run()
							{
								MessageDialog.openError(Display.getDefault().getActiveShell(), "Servoy ApplicationServer version check",
									"Application Server version (" + version + ") is higher than the developers (" + ClientVersion.getReleaseNumber() +
										") \nPlease upgrade the developer Help->Check for updates");
							}
						});
					}
					if (version < ClientVersion.getReleaseNumber())
					{
						Display.getDefault().asyncExec(new Runnable()
						{
							public void run()
							{
								boolean upgrade = MessageDialog.openQuestion(Display.getDefault().getActiveShell(),
									"Servoy ApplicationServer version should be upgraded", "The ApplicationServers version (" + version +
										") is lower than Developer's version (" + ClientVersion.getReleaseNumber() + ")\n Upgrade the ApplicationServer?");

								if (upgrade)
								{
									try
									{
										final int[] updatedToVersion = new int[] { 0 };
										WorkspaceJob job = new WorkspaceJob("Updating Servoy ApplicationServer")
										{
											@Override
											public IStatus runInWorkspace(final IProgressMonitor monitor) throws CoreException
											{
												try
												{
													monitor.beginTask("Updating...", IProgressMonitor.UNKNOWN);
													updatedToVersion[0] = updateAppServerFromSerclipse(new File(appServerDir).getParentFile(), version,
														ClientVersion.getReleaseNumber(), ClientVersion.isLts(), new ActionListener()
														{
															public void actionPerformed(ActionEvent e)
															{
																monitor.worked(1);
															}
														});
												}
												catch (Exception e)
												{
													getLog().log(new Status(IStatus.ERROR, getBundle().getSymbolicName(), "Unexpected error", e));
												}
												return Status.OK_STATUS;
											}
										};
										job.setUser(true);
										job.schedule();
										job.addJobChangeListener(new JobChangeAdapter()
										{
											@Override
											public void done(IJobChangeEvent event)
											{
												if (updatedToVersion[0] < ClientVersion.getReleaseNumber())
												{
													Display.getDefault().asyncExec(new Runnable()
													{
														public void run()
														{
															MessageDialog.openError(new Shell(), "Servoy update problem",
																"Servoy ApplicationServer update failed; please shutdown developer and try to run the command line updater.");
														}
													});
												}
												else
												{
													Display.getDefault().asyncExec(new Runnable()
													{
														public void run()
														{
															if (MessageDialog.openQuestion(Display.getDefault().getActiveShell(), "ApplicationServer updated",
																"It is recommended you restart the workbench for the changes to take effect. Would you like to restart now?"))
															{
																PlatformUI.getWorkbench().restart();
															}
														}
													});
												}
											}
										});
									}
									catch (Exception e)
									{
										Debug.error(e);
									}
								}
							}
						});
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
				getLog().log(new Status(IStatus.ERROR, getBundle().getSymbolicName(), "Unexpected exception", e));
			}
		}
	}

}
