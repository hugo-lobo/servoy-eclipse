/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2016 Servoy BV

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

package com.servoy.eclipse.designer.webpackage.endpoint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import org.apache.wicket.util.string.Strings;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.SpecProviderState;
import org.sablo.specification.SpecReloadSubject.ISpecReloadListener;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebServiceSpecProvider;

import com.servoy.eclipse.core.Activator;
import com.servoy.eclipse.core.IActiveProjectListener;
import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.eclipse.core.resource.WebPackageManagerEditorInput;
import com.servoy.eclipse.core.util.SemVerComparator;
import com.servoy.eclipse.model.ServoyModelFinder;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.eclipse.model.repository.SolutionSerializer;
import com.servoy.eclipse.model.util.AvoidMultipleExecutionsJob;
import com.servoy.eclipse.model.util.ResourcesUtils;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.j2db.ClientVersion;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.Utils;

/**
 * @author gganea
 *
 */
public class GetAllInstalledPackages implements IDeveloperService, ISpecReloadListener, IActiveProjectListener, IWPMController
{
	public static final String CLIENT_SERVER_METHOD = "requestAllInstalledPackages";
	public static final String REFRESH_REMOTE_PACKAGES_METHOD = "refreshRemotePackages";
	public static final String CONTENT_NOT_AVAILABLE_METHOD = "contentNotAvailable";
	public static final String INSTALL_ERROR_METHOD = "installError";
	public static final String MAIN_WEBPACKAGEINDEX = "https://servoy.github.io/webpackageindex/";
	public static final String UNKNOWN_VERSION = "unknown";
	private final WebPackageManagerEndpoint endpoint;
	private static String selectedWebPackageIndex = MAIN_WEBPACKAGEINDEX;
	private AvoidMultipleExecutionsJob reloadWPMSpecsJob;

	private static ConcurrentHashMap<String, Pair<Long, List<JSONObject>>> remotePackagesCache = new ConcurrentHashMap<String, Pair<Long, List<JSONObject>>>();
	private volatile static Job checkForNewRemotePackagesJob;

	public GetAllInstalledPackages(WebPackageManagerEndpoint endpoint)
	{
		this.endpoint = endpoint;
		resourceListenersOn();
	}

	public JSONArray executeMethod(JSONObject msg)
	{
		return getAllInstalledPackages(msg, this, false, false);
	}

	public static JSONArray getAllInstalledPackages(boolean ignoreActiveSolution, boolean includeAllRepositories)
	{
		return getAllInstalledPackages(null, null, ignoreActiveSolution, includeAllRepositories);
	}

	private static List<JSONObject> sortPackages(List<JSONObject> remotePackages)
	{
		remotePackages.sort((JSONObject o1, JSONObject o2) -> {
			boolean b1 = o1.has("top") ? o1.getBoolean("top") : false;
			boolean b2 = o2.has("top") ? o2.getBoolean("top") : false;
			return (b1 ^ b2) ? ((b1 ^ true) ? 1 : -1) : o1.getString("displayName").compareTo(o2.getString("displayName"));
		});
		return remotePackages;
	}

	private static JSONArray getAllInstalledPackages(JSONObject msg, GetAllInstalledPackages getAllInstalledPackagesService,
		boolean ignoreActiveSolution, boolean includeAllRepositories)
	{
		String activeSolutionName = ServoyModelFinder.getServoyModel().getFlattenedSolution().getName();
		ServoyProject[] activeProjecWithModules = ServoyModelManager.getServoyModelManager().getServoyModel().getModulesOfActiveProject();
		SpecProviderState componentSpecProviderState = WebComponentSpecProvider.getSpecProviderState();
		SpecProviderState serviceSpecProviderState = WebServiceSpecProvider.getSpecProviderState();
		JSONArray result = new JSONArray();
		try
		{
			List<JSONObject> remotePackages = getAllInstalledPackagesService != null
				? getAllInstalledPackagesService.getRemotePackagesAndCheckForChanges(includeAllRepositories)
				: getRemotePackages(includeAllRepositories);

			for (JSONObject pack : remotePackages)
			{
				// clear runtime settings
				pack.remove("installed");
				pack.remove("installedIsWPA");
				pack.remove("installedResource");
				pack.remove("activeSolution");

				if (!ignoreActiveSolution)
				{
					String name = pack.getString("name");
					String type = pack.getString("packageType");

					if (activeSolutionName == null && !"Solution-Main".equals(type)) continue;
					if ("Solution".equals(type) || "Solution-Main".equals(type))
					{
						String moduleParent = findModuleParent(ServoyModelFinder.getServoyModel().getFlattenedSolution().getSolution(), name,
							new HashSet<String>());
						ServoyProject solutionProject = ServoyModelManager.getServoyModelManager().getServoyModel().getServoyProject(name);
						pack.put("activeSolution", moduleParent != null ? moduleParent : activeSolutionName);
						if (solutionProject != null && (moduleParent != null || "Solution-Main".equals(type)))
						{
							File wpmPropertiesFile = new File(solutionProject.getProject().getLocation().toFile(), "wpm.properties");
							if (wpmPropertiesFile.exists())
							{
								Properties wpmProperties = new Properties();

								try (FileInputStream wpmfis = new FileInputStream(wpmPropertiesFile))
								{
									wpmProperties.load(wpmfis);
									String version = wpmProperties.getProperty("version");
									if (version != null)
									{
										pack.put("installed", version);
									}
								}
								catch (Exception ex)
								{
									Debug.log(ex);
								}
							}
							else if (!Strings.isEmpty(solutionProject.getSolution().getVersion()))
							{
								pack.put("installed", solutionProject.getSolution().getVersion());
							}
							else
							{
								pack.put("installed", UNKNOWN_VERSION);
							}
						}
					}
					else
					{
						IPackageReader reader = componentSpecProviderState.getPackageReaderForWpm(name);
						if (reader == null) reader = serviceSpecProviderState.getPackageReaderForWpm(name);
						if (reader != null)
						{
							pack.put("installed", reader.getVersion());
							pack.put("installedIsWPA", isWebPackageArchive(activeProjecWithModules, reader.getResource()));
							File installedResource = reader.getResource();
							if (installedResource != null) pack.put("installedResource", installedResource.getName());
							String parentSolutionName = getParentProjectNameForPackage(reader.getResource());
							pack.put("activeSolution", parentSolutionName != null ? parentSolutionName : activeSolutionName);
						}
						else
						{
							pack.put("activeSolution", msg != null && msg.has("solution") ? msg.get("solution") : activeSolutionName);
						}
					}
				}

				result.put(pack);
			}
		}
		catch (Exception e)
		{
			Debug.log(e);
		}
		return result;
	}

	private static boolean isWebPackageArchive(ServoyProject[] activeProjecWithModules, File webPackageFile)
	{
		if (webPackageFile != null && webPackageFile.isFile())
		{
			for (ServoyProject sp : activeProjecWithModules)
			{
				File projectPath = sp.getProject().getLocation().toFile();
				if (webPackageFile.getParentFile().equals(new File(projectPath, SolutionSerializer.NG_PACKAGES_DIR_NAME)))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static String getParentProjectNameForPackage(File packageFile)
	{
		if (packageFile != null && packageFile.isFile())
		{
			IFile file = ResourcesUtils.findFileWithShortestPathForLocationURI(packageFile.toURI());
			if (file != null && file.exists())
			{
				return file.getProject().getName();
			}
		}

		return null;
	}

	private static String findModuleParent(Solution solution, String moduleName, Set<String> alreadyCheckedSolutions)
	{
		if (solution == null) return null;
		alreadyCheckedSolutions.add(solution.getName());
		String[] modules = Utils.getTokenElements(solution.getModulesNames(), ",", true);
		List<String> modulesList = new ArrayList<String>(Arrays.asList(modules));
		if (modulesList.size() == 0)
		{
			return null;
		}
		else if (modulesList.contains(moduleName))
		{
			return solution.getName();
		}
		else
		{
			for (String module : modulesList)
			{
				if (alreadyCheckedSolutions.contains(module)) continue;
				ServoyProject solutionProject = ServoyModelManager.getServoyModelManager().getServoyModel().getServoyProject(module);
				if (solutionProject != null)
				{
					String moduleParent = findModuleParent(solutionProject.getSolution(), moduleName, alreadyCheckedSolutions);
					if (moduleParent != null)
					{
						return moduleParent;
					}
				}
			}
		}
		return null;
	}

	public JSONArray setSelectedWebPackageIndex(String index)
	{
		selectedWebPackageIndex = index;
		try
		{
			return getAllInstalledPackages(null, this, false, false);
		}
		catch (Exception e)
		{
			Debug.log(e);
		}
		return null;
	}


	public List<JSONObject> getRemotePackagesAndCheckForChanges(boolean includeAllRepositories) throws Exception
	{
		return getRemotePackages(endpoint, includeAllRepositories);
	}

	public List<JSONObject> getRemotePackagesAndCheckForChanges() throws Exception
	{
		return getRemotePackagesAndCheckForChanges(false);
	}

	public static List<JSONObject> getRemotePackages(boolean includeAllRepositories) throws Exception
	{
		return getRemotePackages(null, includeAllRepositories);
	}

	public static List<JSONObject> getRemotePackages() throws Exception
	{
		return getRemotePackages(false);
	}

	private static List<JSONObject> getRemotePackages(WebPackageManagerEndpoint endpoint, boolean includeAllRepositories) throws Exception
	{
		List<JSONObject> remotePackages;
		if (includeAllRepositories)
		{
			remotePackages = new ArrayList<JSONObject>();
			String indexes = Activator.getEclipsePreferences().node("wpm").get("indexes", null);
			if (indexes != null)
			{
				JSONArray stored = new JSONArray(indexes);
				for (int i = 0; i < stored.length(); i++)
				{
					JSONObject jsonObject = stored.getJSONObject(i);
					remotePackages.addAll(getRemotePackagesFromRepository(jsonObject.getString("url"), true));
				}
			}
			remotePackages.addAll(getRemotePackagesFromRepository(MAIN_WEBPACKAGEINDEX, true));
		}
		else
		{
			remotePackages = getRemotePackagesFromRepository(selectedWebPackageIndex, true);
		}

		if (endpoint != null)
		{
			// check for new in a new thread
			final String wpIndex = selectedWebPackageIndex;
			if (checkForNewRemotePackagesJob == null)
			{
				checkForNewRemotePackagesJob = new Job("Check for new remote Servoy packages")
				{
					@Override
					protected IStatus run(IProgressMonitor monitor)
					{
						try
						{
							Pair<Long, List<JSONObject>> currentPackages = remotePackagesCache.get(wpIndex);
							if (currentPackages != null)
							{
								List<JSONObject> newRemotePackages = getRemotePackagesFromRepository(wpIndex, false);
								if (newRemotePackages.isEmpty())
								{
									JSONObject jsonResult = new JSONObject();
									jsonResult.put("method", CONTENT_NOT_AVAILABLE_METHOD);
									endpoint.send(jsonResult.toString());
								}
								else if (getChecksum(newRemotePackages) != currentPackages.getLeft().longValue())
								{
									remotePackagesCache.remove(wpIndex);
									JSONObject jsonResult = new JSONObject();
									jsonResult.put("method", REFRESH_REMOTE_PACKAGES_METHOD);
									endpoint.send(jsonResult.toString());
								}
							}
						}
						catch (Exception ex)
						{
							ServoyLog.logError(ex);
						}
						checkForNewRemotePackagesJob = null;
						return Status.OK_STATUS;
					}
				};
				checkForNewRemotePackagesJob.setPriority(Job.LONG);
				checkForNewRemotePackagesJob.schedule();
			}
		}
		return remotePackages;
	}

	private static synchronized List<JSONObject> getRemotePackagesFromRepository(String webPackageIndex, boolean useCache) throws Exception
	{
		if (!useCache || !remotePackagesCache.containsKey(webPackageIndex))
		{
			List<JSONObject> result = new ArrayList<>();
			String repositoriesIndex = getUrlContents(webPackageIndex);

			if (repositoriesIndex != null)
			{
				JSONArray repoArray = new JSONArray(repositoriesIndex);
				for (int i = repoArray.length(); i-- > 0;)
				{
					Object repo = repoArray.get(i);
					if (repo instanceof JSONObject)
					{
						JSONObject repoObject = (JSONObject)repo;
						String packageResponse = getUrlContents(repoObject.getString("url"));
						if (packageResponse == null)
						{
							Debug.error("Couldn't get the package contents of: " + repoObject);
							continue;
						}
						try
						{
							String currentVersion = ClientVersion.getPureVersion();
							JSONObject packageObject = new JSONObject(packageResponse);
							JSONArray jsonArray = packageObject.getJSONArray("releases");

							if (repoObject.has("top")) packageObject.put("top", repoObject.getBoolean("top"));
							else packageObject.put("top", false);
							List<JSONObject> toSort = new ArrayList<>();
							for (int k = jsonArray.length(); k-- > 0;)
							{
								JSONObject jsonObject = jsonArray.getJSONObject(k);
								if (jsonObject.has("servoy-version"))
								{
									String servoyVersion = jsonObject.getString("servoy-version");
									String[] minAndMax = servoyVersion.split(" - ");
									if (SemVerComparator.compare(minAndMax[0], currentVersion) <= 0)
									{
										toSort.add(jsonObject);
									}
								}
								else toSort.add(jsonObject);
							}
							if (toSort.size() > 0)
							{
								Collections.sort(toSort, new Comparator<JSONObject>()
								{
									@Override
									public int compare(JSONObject o1, JSONObject o2)
									{
										return SemVerComparator.compare(o2.optString("version", ""), o1.optString("version", ""));
									}
								});

								List<JSONObject> currentVersionReleases = new ArrayList<>();
								for (JSONObject jsonObject : toSort)
								{
									if (jsonObject.has("servoy-version"))
									{
										String servoyVersion = jsonObject.getString("servoy-version");
										String[] minAndMax = servoyVersion.split(" - ");
										if (minAndMax.length > 1 && SemVerComparator.compare(minAndMax[1], currentVersion) <= 0)
										{
											break;
										}
									}
									currentVersionReleases.add(jsonObject);
								}
								if (currentVersionReleases.size() > 0)
								{
									packageObject.put("releases", currentVersionReleases);
									result.add(packageObject);
								}
							}
						}
						catch (Exception e)
						{
							Debug.log("Couldn't get the package contents of: " + repoObject + " error parsing: " + packageResponse, e);
							continue;
						}
					}
				}
				sortPackages(result);
			}
			else
			{
				remotePackagesCache.remove(webPackageIndex);
				Debug.error("Couldn't get web packages list for: " + webPackageIndex);
			}
			if (useCache)
			{
				if (!result.isEmpty()) remotePackagesCache.put(webPackageIndex, new Pair<Long, List<JSONObject>>(Long.valueOf(getChecksum(result)), result));
			}
			else
			{
				return result;
			}
		}
		Pair<Long, List<JSONObject>> remotePackages = remotePackagesCache.get(webPackageIndex);
		return remotePackages != null ? remotePackages.getRight() : Collections.emptyList(); // could be null if either developer or github/other site are offline
	}

	private static long getChecksum(List<JSONObject> list)
	{
		byte[] listAsBytes = list.toString().getBytes();
		Checksum checksum = new CRC32();
		checksum.update(listAsBytes, 0, listAsBytes.length);

		return checksum.getValue();
	}

	private static String getUrlContents(String urlToRead)
	{
		try
		{
			StringBuilder result = new StringBuilder();
			URL url = new URL(urlToRead);
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			conn.setRequestMethod("GET");
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = rd.readLine()) != null)
			{
				result.append(line);
			}
			rd.close();
			conn.disconnect();
			return result.toString();
		}
		catch (Exception e)
		{
			Debug.error(e);
		}
		return null;
	}

	@Override
	public void dispose()
	{
		resourceListenersOff();
		Job job = checkForNewRemotePackagesJob;
		if (job != null) job.cancel();
	}

	@Override
	public void webObjectSpecificationReloaded()
	{
		synchronized (this)
		{
			// sometimes for example 3 reloads were triggered in a short amount of time; keep the full reloads to a minimum;
			// only keep 1 reload and if that is in progress and another one is needed remember to trigger it only once
			// the first reload is over
			if (reloadWPMSpecsJob == null) reloadWPMSpecsJob = new AvoidMultipleExecutionsJob("Reloading Servoy Package Manager specs...")
			{

				@Override
				protected IStatus runAvoidingMultipleExecutions(IProgressMonitor monitor)
				{
					JSONArray packages = executeMethod(null);
					JSONObject jsonResult = new JSONObject();
					if (packages.length() == 0)
					{
						jsonResult.put("method", GetAllInstalledPackages.CONTENT_NOT_AVAILABLE_METHOD);
					}
					else
					{
						jsonResult.put("method", CLIENT_SERVER_METHOD);
						jsonResult.put("result", packages);
					}
					endpoint.send(jsonResult.toString());

					return Status.OK_STATUS;
				}
			};
		}

		reloadWPMSpecsJob.scheduleIfNeeded();
	}

	@Override
	public boolean activeProjectWillChange(ServoyProject activeProject, ServoyProject toProject)
	{
		return true;
	}

	@Override
	public void activeProjectChanged(ServoyProject activeProject)
	{
		if (activeProject == null)
		{
			closeWPMEditors();
			return;
		}
		if (!"import_placeholder".equals(activeProject.getSolution().getName()))

			webObjectSpecificationReloaded();

	}

	private void closeWPMEditors()
	{
		for (IWorkbenchPage page : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPages())
		{
			for (IEditorReference editorReference : page.getEditorReferences())
			{
				IEditorPart editor = editorReference.getEditor(false);
				if (editor != null)
				{
					if (editor.getEditorInput() instanceof WebPackageManagerEditorInput)
					{
						page.closeEditor(editor, false);
					}
				}
			}
		}
	}

	@Override
	public void activeProjectUpdated(ServoyProject activeProject, int updateInfo)
	{
		if (updateInfo == IActiveProjectListener.MODULES_UPDATED)
		{
			reloadPackages();
		}
	}

	public void resourceListenersOn()
	{
		WebComponentSpecProvider.getSpecReloadSubject().addSpecReloadListener(null, this);
		WebServiceSpecProvider.getSpecReloadSubject().addSpecReloadListener(null, this);
		ServoyModelManager.getServoyModelManager().getServoyModel().addActiveProjectListener(this);
	}

	public void resourceListenersOff()
	{
		WebComponentSpecProvider.getSpecReloadSubject().removeSpecReloadListener(null, this);
		WebServiceSpecProvider.getSpecReloadSubject().removeSpecReloadListener(null, this);
		ServoyModelManager.getServoyModelManager().getServoyModel().removeActiveProjectListener(this);
	}

	public void reloadPackages()
	{
		webObjectSpecificationReloaded();
	}

	public static String getSelectedWebPackageIndex()
	{
		return selectedWebPackageIndex;
	}
}
