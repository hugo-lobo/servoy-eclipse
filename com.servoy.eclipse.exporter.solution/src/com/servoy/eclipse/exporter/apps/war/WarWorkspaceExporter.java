/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2014 Servoy BV

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

package com.servoy.eclipse.exporter.apps.war;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.app.IApplicationContext;

import com.servoy.eclipse.exporter.apps.common.AbstractWorkspaceExporter;
import com.servoy.eclipse.model.ServoyModelFinder;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.model.war.exporter.AbstractWarExportModel;
import com.servoy.eclipse.model.war.exporter.AbstractWarExportModel.License;
import com.servoy.eclipse.model.war.exporter.ExportException;
import com.servoy.eclipse.model.war.exporter.ServerConfiguration;
import com.servoy.eclipse.model.war.exporter.WarExporter;
import com.servoy.eclipse.ngclient.ui.Activator;
import com.servoy.eclipse.ngclient.ui.StringOutputStream;
import com.servoy.eclipse.ngclient.ui.WebPackagesListener;
import com.servoy.j2db.persistence.SolutionMetaData;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IApplicationServerSingleton;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.Utils;

/**
 * Eclipse application that can be used for exporting servoy solutions in .war format.
 * @author gboros
 */
public class WarWorkspaceExporter extends AbstractWorkspaceExporter<WarArgumentChest>
{

	private final class CommandLineWarExportModel extends AbstractWarExportModel
	{
		private final WarArgumentChest configuration;
		private Set<String> exportedPackages;

		private CommandLineWarExportModel(WarArgumentChest configuration, boolean isNGExport)
		{
			super(isNGExport);
			this.configuration = configuration;
			search();
		}

		@Override
		public boolean isExportActiveSolution()
		{
			return configuration.isExportActiveSolutionOnly();
		}

		@Override
		public String getStartRMIPort()
		{
			return null;
		}

		@Override
		public boolean getStartRMI()
		{
			return false;
		}

		@Override
		public String getServoyPropertiesFileName()
		{
			String warSettingsFileName = configuration.getWarSettingsFileName();
			if (warSettingsFileName == null)
			{
				String servoyPropertiesFileName = configuration.getSettingsFileName();
				if (servoyPropertiesFileName == null)
				{
					servoyPropertiesFileName = getServoyApplicationServerDir() + File.separator + "servoy.properties";
				}
				return servoyPropertiesFileName;
			}
			return warSettingsFileName;
		}

		@Override
		public String getWebXMLFileName()
		{
			return configuration.getWebXMLFileName();
		}

		@Override
		public String getLog4jConfigurationFile()
		{
			return configuration.getLog4jConfigurationFile();
		}

		@Override
		public ServerConfiguration getServerConfiguration(String serverName)
		{
			return null;
		}

		@Override
		public SortedSet<String> getSelectedServerNames()
		{
			return null;
		}

		@Override
		public boolean isExportNonActiveSolutions()
		{
			return configuration.getNoneActiveSolutions() != null;
		}

		public List<String> getNonActiveSolutions()
		{
			if (configuration.getNoneActiveSolutions() != null)
			{
				return Arrays.asList(configuration.getNoneActiveSolutions().split(" "));
			}
			return Collections.emptyList();
		}

		@Override
		public List<String> getPlugins()
		{
			return getFilteredFileNames(ApplicationServerRegistry.get().getPluginManager().getPluginsDir(), configuration.getExcludedPlugins(),
				configuration.getPlugins());
		}

		@Override
		public List<String> getLafs()
		{
			return getFilteredFileNames(ApplicationServerRegistry.get().getLafManager().getLAFDir(), configuration.getExcludedLafs(), configuration.getLafs());
		}

		@Override
		public String getWarFileName()
		{
			String warFileName = configuration.getWarFileName();
			if (warFileName == null)
			{
				ServoyProject activeProject = ServoyModelFinder.getServoyModel().getActiveProject();
				warFileName = activeProject.getProject().getName();
			}
			if (!warFileName.endsWith(".war")) warFileName += ".war";
			return configuration.getExportFilePath() + File.separator + warFileName;
		}

		@Override
		public List<String> getDrivers()
		{
			return getFilteredFileNames(ApplicationServerRegistry.get().getServerManager().getDriversDir(), configuration.getExcludedDrivers(),
				configuration.getDrivers());
		}

		@Override
		public List<String> getBeans()
		{
			return getFilteredFileNames(ApplicationServerRegistry.get().getBeanManager().getBeansDir(), configuration.getExcludedBeans(),
				configuration.getBeans());
		}

		@Override
		public boolean allowOverwriteSocketFactoryProperties()
		{
			return false;
		}

		@Override
		public String getServoyApplicationServerDir()
		{
			return configuration.getAppServerDir();
		}

		List<String> getFilteredFileNames(File folder, String excluded, String included)
		{
			Set<String> names = null;
			if (excluded != null)
			{
				//if <none> it will not match anything and return all files
				return getFiles(folder, new HashSet<String>(Arrays.asList(excluded.toLowerCase().split(" "))), true);
			}
			if (included != null)
			{
				if ("<none>".equals(included.toLowerCase()))
				{
					return Collections.emptyList();
				}
				names = new HashSet<String>(Arrays.asList(included.toLowerCase().split(" ")));
			}
			return getFiles(folder, names, false);
		}

		List<String> getFiles(File dir, final Set<String> fileNames, boolean exclude)
		{
			String[] list = dir.list(new FilenameFilter()
			{
				public boolean accept(File d, String name)
				{
					boolean accept = fileNames != null ? (exclude ? !fileNames.contains(name.toLowerCase()) : fileNames.contains(name.toLowerCase())) : true;

					return accept &&
						(name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".zip") || (new File(d.getPath(), name).isDirectory()));
				}
			});
			if (list == null || list.length == 0) return Collections.emptyList();
			return Arrays.asList(list);
		}

		@Override
		public List<String> getPluginLocations()
		{
			return Arrays.asList(configuration.getPluginLocations().split(" "));
		}

		@Override
		public Set<String> getExportedComponents()
		{
			if (configuration.getExcludedComponentPackages() != null)
			{
				List<String> excludedPackages = Arrays.asList(configuration.getExcludedComponentPackages().split(" "));
				return Arrays.stream(componentsSpecProviderState.getAllWebComponentSpecifications()) //
					.filter(spec -> !excludedPackages.contains(spec.getPackageName())) //
					.map(spec -> spec.getName()) //
					.collect(Collectors.toSet());
			}
			else
			{
				if (configuration.getSelectedComponents() == null || configuration.getSelectedComponents().equals("")) return getUsedComponents();

				Set<String> set = new HashSet<String>();
				if (configuration.getSelectedComponents().trim().equalsIgnoreCase("all"))
				{
					Arrays.stream(componentsSpecProviderState.getAllWebComponentSpecifications()).map(spec -> spec.getName()).forEach(set::add);
				}
				else
				{
					set.addAll(Arrays.asList(configuration.getSelectedComponents().split(" ")));
					for (String componentName : set)
					{
						if (componentsSpecProviderState.getWebComponentSpecification(componentName) == null)
						{
							// TODO shouldn't this be an error and shouldn't the exporter fail nicely with a new exit code? I'm thinking now of Jenkins usage and failing sooner rather then later (at export rather then when testing)
							output("'" + componentName + "' is not a valid component name or it could not be found. Ignoring.");
							set.remove(componentName);
						}
					}
					set.addAll(getUsedComponents());
				}
				return set;
			}
		}

		@Override
		public Set<String> getExportedServices()
		{
			if (configuration.getExcludedServicePackages() != null)
			{
				List<String> excludedPackages = Arrays.asList(configuration.getExcludedServicePackages().split(" "));
				return Arrays.stream(servicesSpecProviderState.getAllWebComponentSpecifications()) //
					.filter(spec -> !excludedPackages.contains(spec.getPackageName())) //
					.map(spec -> spec.getName()).collect(Collectors.toSet());
			}
			else
			{
				if (configuration.getSelectedServices() == null || configuration.getSelectedServices().equals("")) return getUsedServices();
				Set<String> set = new HashSet<String>();
				if (configuration.getSelectedServices().trim().equalsIgnoreCase("all"))
				{
					Arrays.stream(servicesSpecProviderState.getAllWebComponentSpecifications()).map(spec -> spec.getName()).forEach(set::add);
				}
				else
				{
					set.addAll(Arrays.asList(configuration.getSelectedServices().split(" ")));
					for (String serviceName : set)
					{
						if (servicesSpecProviderState.getWebComponentSpecification(serviceName) == null)
						{
							// TODO shouldn't this be an error and shouldn't the exporter fail nicely with a new exit code? I'm thinking now of Jenkins usage and failing sooner rather then later (at export rather then when testing)
							output("'" + serviceName + "' is not a valid service name or it could not be found. Ignoring.");
							set.remove(serviceName);
						}
					}
					set.addAll(getUsedServices());
				}
				return set;
			}
		}

		@Override
		public String getFileName()
		{
			return null;
		}

		@Override
		public String exportNG2Mode()
		{
			return configuration.exportNG2Mode();
		}

		@Override
		public boolean isExportMetaData()
		{
			return configuration.shouldExportMetadata();
		}

		@Override
		public boolean isExportSampleData()
		{
			return configuration.isExportSampleData();
		}

		@Override
		public boolean isExportI18NData()
		{
			return configuration.isExportI18NData();
		}

		@Override
		public int getNumberOfSampleDataExported()
		{
			return configuration.getNumberOfSampleDataExported();
		}

		@Override
		public boolean isExportAllTablesFromReferencedServers()
		{
			return configuration.isExportAllTablesFromReferencedServers();
		}

		@Override
		public boolean isCheckMetadataTables()
		{
			return configuration.checkMetadataTables();
		}

		@Override
		public boolean isExportUsingDbiFileInfoOnly()
		{
			return configuration.shouldExportUsingDbiFileInfoOnly();
		}

		@Override
		public boolean isExportUsers()
		{
			return configuration.exportUsers();
		}

		@Override
		public boolean isOverwriteGroups()
		{
			return configuration.isOverwriteGroups();
		}

		@Override
		public boolean isAllowSQLKeywords()
		{
			return configuration.isAllowSQLKeywords();
		}

		@Override
		public boolean isOverrideSequenceTypes()
		{
			return configuration.isOverrideSequenceTypes();
		}

		@Override
		public boolean isInsertNewI18NKeysOnly()
		{
			return configuration.isInsertNewI18NKeysOnly();
		}

		@Override
		public boolean isOverrideDefaultValues()
		{
			return configuration.isOverrideDefaultValues();
		}

		@Override
		public int getImportUserPolicy()
		{
			return configuration.getImportUserPolicy();
		}

		@Override
		public boolean isAddUsersToAdminGroup()
		{
			return configuration.isAddUsersToAdminGroup();
		}

		@Override
		public String getAllowDataModelChanges()
		{
			if (configuration.getAllowDataModelChanges() != null)
			{
				return configuration.getAllowDataModelChanges();
			}
			return Boolean.toString(!configuration.isStopOnAllowDataModelChanges());
		}

		@Override
		public boolean isUpdateSequences()
		{
			return configuration.isUpdateSequences();
		}

		@Override
		public boolean isAutomaticallyUpgradeRepository()
		{
			return configuration.automaticallyUpdateRepository();
		}

		@Override
		public String getTomcatContextXMLFileName()
		{
			return configuration.getTomcatContextXMLFileName();
		}

		@Override
		public boolean isCreateTomcatContextXML()
		{
			return configuration.isCreateTomcatContextXML();
		}

		@Override
		public boolean isClearReferencesStatic()
		{
			return configuration.isClearReferencesStatic();
		}

		@Override
		public boolean isClearReferencesStopThreads()
		{
			return configuration.isClearReferencesStopThreads();
		}

		@Override
		public boolean isClearReferencesStopTimerThreads()
		{
			return configuration.isClearReferencesStopTimerThreads();
		}

		@Override
		public boolean isAntiResourceLocking()
		{
			return configuration.isAntiResourceLocking();
		}

		@Override
		public String getDefaultAdminUser()
		{
			return configuration.getDefaultAdminUser();
		}

		@Override
		public String getDefaultAdminPassword()
		{
			return configuration.getDefaultAdminPassword();
		}

		@Override
		public boolean isUseAsRealAdminUser()
		{
			return configuration.isUseAsRealAdminUser();
		}

		@Override
		public Collection<License> getLicenses()
		{
			return configuration.getLicenses().values();
		}

		@Override
		public boolean isOverwriteDeployedDBServerProperties()
		{
			return configuration.isOverwriteDeployedDBServerProperties();
		}

		@Override
		public boolean isOverwriteDeployedServoyProperties()
		{
			return configuration.isOverwriteDeployedServoyProperties();
		}

		@Override
		public String getUserHome()
		{
			return configuration.getUserHome();
		}

		@Override
		public boolean isSkipDatabaseViewsUpdate()
		{
			return configuration.skipDatabaseViewsUpdate();
		}

		@Override
		public Set<String> getExportedPackages()
		{
			if (exportedPackages == null)
			{
				exportedPackages = Stream.of(getExportedComponents().stream() //
					.map(comp -> componentsSpecProviderState.getWebComponentSpecification(comp).getPackageName()) //
					.collect(Collectors.toSet()), //
					getExportedServices().stream() //
						.map(comp -> servicesSpecProviderState.getWebComponentSpecification(comp).getPackageName()) //
						.collect(Collectors.toSet())) //
					.flatMap(Set::stream) //
					.collect(Collectors.toSet());
				exportedPackages.addAll(exportedLayoutPackages);
			}
			return exportedPackages;
		}

		@Override
		public void displayWarningMessage(String title, String message)
		{
			System.out.println(title + ": " + message);
		}
	}

	@Override
	protected WarArgumentChest createArgumentChest(IApplicationContext context)
	{
		return new WarArgumentChest((String[])context.getArguments().get(IApplicationContext.APPLICATION_ARGS));
	}

	private void checkAndAutoUpgradeLicenses(CommandLineWarExportModel exportModel) throws ExportException
	{
		IApplicationServerSingleton server = ApplicationServerRegistry.get();
		for (License l : exportModel.getLicenses())
		{
			if (!server.checkClientLicense(l.getCompanyKey(), l.getCode(), l.getNumberOfLicenses()))
			{
				//try to auto upgrade
				Pair<Boolean, String> code = server.upgradeLicense(l.getCompanyKey(), l.getCode(), l.getNumberOfLicenses());
				if (code == null || !code.getLeft().booleanValue())
				{
					throw new ExportException("Cannot export! License '" + l.getCompanyKey() + "' with code " + l.getCode() +
						(code != null && !code.getLeft().booleanValue() ? " error: " + code.getRight() : " is not valid."));
				}
				else if (code.getLeft().booleanValue() && !l.getCode().equals(code.getRight()))
				{
					output("License '" + l.getCompanyKey() + "' with code " + l.getCode() + " was auto upgraded to " + code.getRight() +
						". Please change it to the new code in future exports.");
					exportModel.replaceLicenseCode(l, code.getRight());
				}
			}
		}
		String checkFile = exportModel.checkServoyPropertiesFileExists();
		if (checkFile == null)
		{
			final Object[] upgrade = exportModel.checkAndAutoUpgradeLicenses();
			if (upgrade != null && upgrade.length >= 3)
			{
				if (!Utils.getAsBoolean(upgrade[0]))
				{
					throw new ExportException(
						"License code '" + upgrade[1] + "' defined in the selected properties file is invalid." + (upgrade[2] != null ? upgrade[2] : ""));
				}
				else
				{
					output("Could not save changes to the properties file. License code '" + upgrade[1] + "' was auto upgraded to '" + upgrade[2] +
						"'. The export contains the new license code, but the changes could not be written to the selected properties file. Please adjust the '" +
						exportModel.getServoyPropertiesFileName() + "' file manually.");
				}
			}
		}
		else
		{
			throw new ExportException("Error creating the WAR file. " + checkFile);
		}
	}

	@Override
	protected void checkAndExportSolutions(WarArgumentChest configuration)
	{
		if (configuration.exportNG2Mode() != null)
		{
			WebPackagesListener.setIgnore(true);
			Activator.getInstance().setConsole(() -> new StringOutputStream()
			{
				@Override
				public void write(CharSequence chars) throws IOException
				{
					outputExtra(chars.toString().trim());
				}

				@Override
				public void close() throws IOException
				{
				}
			});
			Activator.getInstance().extractNode();
			Activator.getInstance().copyNodeFolder(false, true);
		}
		super.checkAndExportSolutions(configuration);
	}

	@Override
	protected void exportActiveSolution(final WarArgumentChest configuration)
	{
		boolean isNGExport = false;
		ServoyProject activeProject = ServoyModelFinder.getServoyModel().getActiveProject();
		if (activeProject != null)
		{
			int solutionType = activeProject.getSolutionMetaData().getSolutionType();
			isNGExport = solutionType != SolutionMetaData.WEB_CLIENT_ONLY && solutionType != SolutionMetaData.SMART_CLIENT_ONLY;
		}

		try
		{
			CommandLineWarExportModel exportModel = new CommandLineWarExportModel(configuration, isNGExport);
			checkAndAutoUpgradeLicenses(exportModel);

			WarExporter warExporter = new WarExporter(exportModel);

			warExporter.doExport(new IProgressMonitor()
			{
				@Override
				public void worked(int work)
				{
				}

				@Override
				public void subTask(String name)
				{
					outputExtra(name);
				}

				@Override
				public void setTaskName(String name)
				{
					outputExtra(name);
				}

				@Override
				public void setCanceled(boolean value)
				{
				}

				@Override
				public boolean isCanceled()
				{
					return false;
				}

				@Override
				public void internalWorked(double work)
				{
				}

				@Override
				public void done()
				{
				}

				@Override
				public void beginTask(String name, int totalWork)
				{
					outputExtra(name);
				}
			});
		}
		catch (ExportException ex)
		{
			ServoyLog.logError("Failed to export solution.", ex);
			outputError("Exception while exporting solution: " + ex.getMessage() + ".  EXPORT FAILED for this solution. Check workspace log for more info.");
			exitCode = EXIT_EXPORT_FAILED;
		}
	}
}
