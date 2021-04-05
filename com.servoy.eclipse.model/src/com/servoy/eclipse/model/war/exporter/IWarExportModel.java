package com.servoy.eclipse.model.war.exporter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.crypto.Cipher;

import com.servoy.eclipse.model.export.IExportSolutionModel;
import com.servoy.eclipse.model.war.exporter.AbstractWarExportModel.License;


/**
 * WAR export model
 *
 * @author gboros
 * @since 8.0
 */
public interface IWarExportModel extends IExportSolutionModel
{
	public String enc_prefix = "encrypted:";//keep in sync with Settings.enc_prefix

	public boolean isExportActiveSolution();

	public String getWarFileName();

	public String getServoyPropertiesFileName();

	public String getServoyApplicationServerDir();

	public boolean allowOverwriteSocketFactoryProperties();

	public List<String> getDrivers();

	public List<String> getLafs();

	public List<String> getPlugins();

	public List<String> getBeans();

	public boolean getStartRMI();

	public String getStartRMIPort();

	public SortedSet<String> getSelectedServerNames();

	public ServerConfiguration getServerConfiguration(String serverName);

	public List<String> getPluginLocations();

	public Set<String> getComponentsUsedExplicitlyBySolution();

	public Set<String> getComponentsNeededUnderTheHood();

	public Set<String> getServicesUsedExplicitlyBySolution();

	/**
	 * NOTE: this currently won't include any sablo content as all needed sablo js files are referenced staticly from the page when serving ng clients.
	 */
	public Set<String> getServicesNeededUnderTheHood();

	public boolean isOverwriteGroups();

	public boolean isAllowSQLKeywords();

	public boolean isOverrideSequenceTypes();

	public boolean isInsertNewI18NKeysOnly();

	public boolean isOverrideDefaultValues();

	public int getImportUserPolicy();

	public boolean isAddUsersToAdminGroup();

	public String getAllowDataModelChanges();

	public boolean isUpdateSequences();

	boolean isAutomaticallyUpgradeRepository();

	boolean isCreateTomcatContextXML();

	boolean isAntiResourceLocking();

	boolean isClearReferencesStatic();

	boolean isClearReferencesStopThreads();

	boolean isClearReferencesStopTimerThreads();

	/**
	 * admin properties
	 */
	public String getDefaultAdminUser();

	public String getDefaultAdminPassword();

	public boolean isUseAsRealAdminUser();

	Collection<License> getLicenses();

	String decryptPassword(Cipher desCipher, String code);

	public boolean isNGExport();

	public void setUserHome(String userHome);

	public String getUserHome();

	public void setOverwriteDeployedDBServerProperties(boolean isOverwriteDeployedDBServerProperties);

	public boolean isOverwriteDeployedDBServerProperties();

	public void setOverwriteDeployedServoyProperties(boolean isOverwriteDeployedServoyProperties);

	public boolean isOverwriteDeployedServoyProperties();

	public String getWebXMLFileName();

	public String getLog4jConfigurationFile();

	public String checkWebXML();

	public String checkLog4jConfigurationFile();

	public List<String> getNonActiveSolutions();

	public boolean isExportNonActiveSolutions();

	public Map<String, String> getUpgradedLicenses();

	public boolean isSkipDatabaseViewsUpdate();

	public void setSkipDatabaseViewsUpdate(boolean skip);

	public Set<String> getExportedPackages();

	/**
	 * Gets all components that are to be exported. This includes under-the-hood components, components that are explicitly used by solution and any
	 * optional components that the user picked during export.
	 */
	Set<String> getAllExportedComponents();

	/**
	 * Gets all services that are to be exported. This includes under-the-hood services, services that are explicitly used by solution and any
	 * optional services that the user picked during export.
	 */
	Set<String> getAllExportedServices();

}
