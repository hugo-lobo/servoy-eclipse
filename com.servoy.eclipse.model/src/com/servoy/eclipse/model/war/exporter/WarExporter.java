/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2011 Servoy BV

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

package com.servoy.eclipse.model.war.exporter;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.json.JSONException;
import org.sablo.IndexPageEnhancer;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.PackageSpecification;
import org.sablo.specification.SpecProviderState;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebLayoutSpecification;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.WebServiceSpecProvider;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.servoy.eclipse.model.ServoyModelFinder;
import com.servoy.eclipse.model.extensions.IServoyModel;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.eclipse.model.ngpackages.BaseNGPackageManager;
import com.servoy.eclipse.model.repository.EclipseExportI18NHelper;
import com.servoy.eclipse.model.repository.EclipseExportUserChannel;
import com.servoy.eclipse.model.repository.SolutionSerializer;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.model.util.TableDefinitionUtils;
import com.servoy.eclipse.model.util.WorkspaceFileAccess;
import com.servoy.eclipse.model.war.exporter.AbstractWarExportModel.License;
import com.servoy.j2db.ClientVersion;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.IBeanManagerInternal;
import com.servoy.j2db.ILAFManagerInternal;
import com.servoy.j2db.persistence.AbstractRepository;
import com.servoy.j2db.persistence.Media;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.persistence.SolutionMetaData;
import com.servoy.j2db.server.headlessclient.dataui.TemplateGenerator;
import com.servoy.j2db.server.ngclient.ComponentsModuleGenerator;
import com.servoy.j2db.server.ngclient.NGClientEntryFilter;
import com.servoy.j2db.server.ngclient.NGClientWebsocketSession;
import com.servoy.j2db.server.ngclient.startup.resourceprovider.ComponentResourcesExporter;
import com.servoy.j2db.server.ngclient.startup.resourceprovider.ResourceProvider;
import com.servoy.j2db.server.ngclient.utils.NGUtils;
import com.servoy.j2db.server.shared.ApplicationServerRegistry;
import com.servoy.j2db.server.shared.IApplicationServerSingleton;
import com.servoy.j2db.server.shared.IUserManager;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.JarManager;
import com.servoy.j2db.util.JarManager.ExtensionResource;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.SecuritySupport;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.SortedProperties;
import com.servoy.j2db.util.Utils;
import com.servoy.j2db.util.xmlxport.IMetadataDefManager;
import com.servoy.j2db.util.xmlxport.ITableDefinitionsManager;
import com.servoy.j2db.util.xmlxport.IXMLExporter;


/**
 * Class that creates the WAR file.
 *
 * @author gboros
 * @since 8.0
 */
public class WarExporter
{
	private static final String[] EXCLUDE_FROM_NG_JAR = new String[] { "com/servoy/j2db/server/ngclient/startup", "war/", "META-INF/MANIFEST.", "META-INF/SERVOYCL." };
	private static final String[] NG_LIBS = new String[] { "slf4j.api_*.jar", "org.apache.logging.log4j.*.jar", "org.freemarker*.jar", "servoy_ngclient_" +
		ClientVersion.getBundleVersionWithPostFix() +
		".jar", "sablo_" + ClientVersion.getBundleVersionWithPostFix() + ".jar", "commons-lang3_*.jar", "wro4j-core_*.jar" };

	private static final String WRO4J_RUNNER = "wro4j-runner-1.7.7";

	private final IWarExportModel exportModel;
	private SpecProviderState componentsSpecProviderState;
	private SpecProviderState servicesSpecProviderState;
	private Set<File> pluginFiles = new HashSet<>();

	public WarExporter(IWarExportModel exportModel)
	{
		this.exportModel = exportModel;

		if (exportModel.isNGExport())
		{
			this.componentsSpecProviderState = WebComponentSpecProvider.getSpecProviderState();
			this.servicesSpecProviderState = WebServiceSpecProvider.getSpecProviderState();
		}
	}

	/**
	 * Export the solution as war.
	 * @param monitor
	 * @throws ExportException if export fails
	 */
	public void doExport(IProgressMonitor m) throws ExportException
	{
		SubMonitor monitor = SubMonitor.convert(m, "Creating War File", 39);
		File warFile = createNewWarFile();
		monitor.worked(2);
		File tmpWarDir = createTempDir();
		monitor.worked(2);
		String appServerDir = exportModel.getServoyApplicationServerDir();
		monitor.subTask("Copy root webapp files");
		copyRootWebappFiles(tmpWarDir, appServerDir);
		monitor.worked(2);
		monitor.subTask("Copy beans");
		copyBeans(tmpWarDir, appServerDir);
		monitor.worked(2);
		monitor.subTask("Copy plugins");
		copyPlugins(tmpWarDir, appServerDir);
		monitor.worked(2);
		monitor.subTask("Copy lafs");
		copyLafs(tmpWarDir, appServerDir);
		monitor.worked(2);
		monitor.subTask("Copy all standard libraries");
		final File targetLibDir = copyStandardLibs(tmpWarDir, appServerDir);
		monitor.worked(2);
		monitor.subTask("Copy Drivers");
		copyDrivers(appServerDir, targetLibDir);
		monitor.worked(2);
		monitor.subTask("Copy images");
		copyLibImages(tmpWarDir, appServerDir);
		monitor.worked(2);
		moveSlf4j(tmpWarDir, targetLibDir);
		monitor.worked(2);
		monitor.subTask("Creating web.xml");
		copyWebXml(tmpWarDir);
		monitor.worked(2);
		monitor.subTask("Creating log4j.xml");
		copyLog4jXml(tmpWarDir);
		monitor.worked(2);
		monitor.subTask("Creating context.xml");
		createTomcatContextXML(tmpWarDir);
		monitor.worked(2);
		addServoyProperties(tmpWarDir);
		monitor.worked(2);
		if (exportModel.isExportActiveSolution())
		{
			monitor.subTask("Copy the active solution");
			copyActiveSolution(monitor.newChild(2), tmpWarDir);
		}

		exportAdminUser(tmpWarDir);

		monitor.setWorkRemaining(exportModel.isNGExport() ? 9 : 3);
		if (exportModel.isNGExport())
		{
			monitor.subTask("Copying NGClient components/services...");
			copyComponentsAndServicesPlusLibs(monitor.newChild(2), tmpWarDir, targetLibDir);
			monitor.setWorkRemaining(6);
			monitor.subTask("Copy exported components");
			copyExportedComponentsAndServicesPropertyFile(tmpWarDir);
			monitor.worked(2);
			monitor.subTask("Grouping JS and CSS resources");
			copyMinifiedAndGrouped(tmpWarDir);
			monitor.subTask("Compile less resources");
			compileLessResources(tmpWarDir);
			monitor.worked(1);
		}
		monitor.subTask("Creating deploy properties");
		createDeployPropertiesFile(tmpWarDir);
		monitor.worked(1);
		monitor.subTask("Creating/zipping the WAR file");
		zipDirectory(tmpWarDir, warFile);
		monitor.worked(2);
		deleteDirectory(tmpWarDir);
		monitor.worked(1);
		monitor.done();
		return;
	}

	/**
	 * @param tmpWarDir
	 */
	private void compileLessResources(File tmpWarDir)
	{
		IServoyModel servoyModel = ServoyModelFinder.getServoyModel();
		FlattenedSolution fs = servoyModel.getFlattenedSolution();
		Iterator<Media> it = fs.getMedias(false);
		while (it.hasNext())
		{
			Media media = it.next();
			if (media.getName().endsWith(".less"))
			{
				String content = ResourceProvider.compileLessWithNashorn(new String(media.getMediaData()));
				if (content != null)
				{
					File folder = new File(tmpWarDir, NGClientWebsocketSession.SERVOY_SOLUTION_CSS);
					if (!folder.exists() && !folder.mkdir())
					{
						Debug.error("Could not create folder " + folder.getName());
						break;
					}
					try
					{
						File f = new File(folder, media.getName().replace(".less", ".css"));
						f.createNewFile();
						try (PrintWriter printWriter = new PrintWriter(f))
						{
							printWriter.println(content);
						}
						catch (FileNotFoundException e)
						{
							ServoyLog.logError(e);
						}
					}
					catch (IOException e)
					{
						ServoyLog.logError(e);
					}
				}
			}
		}
	}

	/**
	 * Group and minify (if checked) the JS and CSS resources.
	 * @param tmpWarDir
	 * @throws ExportException
	 */
	private void copyMinifiedAndGrouped(File tmpWarDir) throws ExportException
	{
		try
		{
			String id = Long.toHexString(System.currentTimeMillis());
			try
			{
				File groupProperties = new File(tmpWarDir, "WEB-INF/groupid.properties");
				Properties prop = new Properties();
				prop.setProperty("groupid", id);

				try (FileWriter writer = new FileWriter(groupProperties))
				{
					prop.store(writer, "group properties");
				}
			}
			catch (Exception e)
			{
				ServoyLog.logError(e);
			}

			//generate servoy-components.js
			File componentsFile = new File(tmpWarDir, "js/servoy-components.js");
			StringBuilder sb = ComponentsModuleGenerator.generateComponentsModule(exportModel.getExportedServices(), exportModel.getExportedComponents());
			FileUtils.copyInputStreamToFile(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)), componentsFile);

			//generate wro.xml
			String warDirPath = tmpWarDir.getAbsolutePath();
			File wroFile = generateWroXml(tmpWarDir, id);

			//copy the wro4j command line runner to the war
			File jarFile = new File(tmpWarDir, WRO4J_RUNNER);
			FileUtils.copyInputStreamToFile(WarExporter.class.getResource("resources/" + WRO4J_RUNNER).openStream(), jarFile);
			File wroPropertiesFile = new File(tmpWarDir, "wro.properties");
			FileUtils.copyInputStreamToFile(WarExporter.class.getResource("resources/wro.properties").openStream(), wroPropertiesFile);

			String pathSeparator = System.getProperty("file.separator");
			String java = System.getProperty("java.home") + pathSeparator + "bin" + pathSeparator + "java";
			File javaFile = new File(java);
			if (!javaFile.exists())
			{
				javaFile = new File(java + ".exe");//windows
				java = javaFile.exists() ? java + ".exe" : "java";
			}

			List<String> args = new ArrayList<String>();
			args.add(java);
			args.add("-jar");
			args.add(jarFile.getAbsolutePath());
			args.add("--contextFolder");
			args.add(warDirPath);
			args.add("--destinationFolder");
			File dest = new File(tmpWarDir, "wro");
			args.add(dest.getAbsolutePath());
			args.add("--wroFile");
			args.add(wroFile.getAbsolutePath());
			args.add("--wroConfigurationFile");
			args.add(wroPropertiesFile.getAbsolutePath());
			args.add("-m");
			args.add("-c");
			String processors = "semicolonAppender";
			if (exportModel.isMinimizeJsCssResources()) processors += ",jsMin,yuiCssMin";
			processors += ",cssDataUri";
			args.add(processors);

			ProcessBuilder builder = new ProcessBuilder(args);
			builder.redirectErrorStream(true);
			Process proc = builder.start();

			String line = null;
			BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			StringBuilder message = new StringBuilder();
			while ((line = in.readLine()) != null)
			{
				message.append(line);
			}
			in.close();
			if (proc.waitFor() != 0)
			{
				Debug.error(message);
				throw new ExportException(
					"Could not group and minify JS and CSS resources. See log for more details and servoy wiki on how to exclude libraries from grouping using group property in the spec.");
			}

			//delete unneeded files
			try
			{
				Files.delete(wroFile.toPath());
			}
			catch (Exception e)
			{
				// ignore will try to delete on exit later on.
			}
			try
			{
				Files.delete(jarFile.toPath());
			}
			catch (Exception e)
			{
				// ignore will try to delete on exit later on.
			}
			try
			{
				Files.delete(wroPropertiesFile.toPath());
			}
			catch (Exception e)
			{
				// ignore will try to delete on exit later on.
			}
		}
		catch (Exception e)
		{
			Debug.error(e);
			throw new ExportException(e.getMessage(), e);
		}
	}

	private File generateWroXml(File tmpWarDir, String id)
		throws ParserConfigurationException, TransformerFactoryConfigurationError, TransformerConfigurationException, TransformerException
	{
		File wroFile = new File(tmpWarDir, "wro.xml");
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		// root elements
		Document doc = docBuilder.newDocument();
		Element rootElement = doc.createElement("groups");
		doc.appendChild(rootElement);
		Attr attr = doc.createAttribute("xmlns");
		attr.setValue("http://www.isdc.ro/wro");
		rootElement.setAttributeNode(attr);

		Set<String> exportedWebObjects = null;
		if (exportModel.getExportedComponents() != null || exportModel.getExportedServices() != null)
		{
			exportedWebObjects = new HashSet<>();
			if (exportModel.getExportedComponents() != null) exportedWebObjects.addAll(exportModel.getExportedComponents());
			if (exportModel.getExportedServices() != null) exportedWebObjects.addAll(exportModel.getExportedServices());
		}
		Object[] allContributions = IndexPageEnhancer.getAllContributions(exportedWebObjects, Boolean.TRUE, NGClientEntryFilter.CONTRIBUTION_ENTRY_FILTER);
		Element group = doc.createElement("group");
		rootElement.appendChild(group);
		attr = doc.createAttribute("name");
		attr.setValue(NGClientEntryFilter.SERVOY_THIRDPARTY_SVYGRP + id);
		group.setAttributeNode(attr);
		for (String relativePath : NGClientEntryFilter.INDEX_3TH_PARTY_JS)
		{
			addGroupElement(doc, group, tmpWarDir, "/" + relativePath, "js");
		}

		group = doc.createElement("group");
		rootElement.appendChild(group);
		attr = doc.createAttribute("name");
		attr.setValue(NGClientEntryFilter.SERVOY_APP_SVYGRP + id);
		group.setAttributeNode(attr);
		for (String relativePath : NGClientEntryFilter.INDEX_SERVOY_JS)
		{
			addGroupElement(doc, group, tmpWarDir, "/" + relativePath, "js");
		}

		@SuppressWarnings("unchecked")
		Collection<String> jsContributions = (Collection<String>)allContributions[1];
		if (jsContributions != null)
		{
			group = doc.createElement("group");
			rootElement.appendChild(group);
			attr = doc.createAttribute("name");
			attr.setValue(NGClientEntryFilter.SERVOY_CONTRIBUTIONS_SVYGRP + id);
			group.setAttributeNode(attr);
			for (String relativePath : jsContributions)
			{
				if (relativePath.startsWith("sablo")) continue;//exclude sablo from group, is used from .jar
				addGroupElement(doc, group, tmpWarDir, "/" + relativePath, "js");
			}
		}

		group = doc.createElement("group");
		rootElement.appendChild(group);
		attr = doc.createAttribute("name");
		attr.setValue(NGClientEntryFilter.SERVOY_CSS_THIRDPARTY_SVYGRP + id);
		group.setAttributeNode(attr);
		for (String relativePath : NGClientEntryFilter.INDEX_3TH_PARTY_CSS)
		{
			addGroupElement(doc, group, tmpWarDir, "/" + relativePath, "css");
		}

		@SuppressWarnings("unchecked")
		Collection<String> cssContributions = (Collection<String>)allContributions[0];
		cssContributions.add(NGClientEntryFilter.SERVOY_CSS);
		if (cssContributions != null)
		{
			group = doc.createElement("group");
			rootElement.appendChild(group);
			attr = doc.createAttribute("name");
			attr.setValue(NGClientEntryFilter.SERVOY_CSS_CONTRIBUTIONS_SVYGRP + id);
			group.setAttributeNode(attr);
			for (String relativePath : cssContributions)
			{
				addGroupElement(doc, group, tmpWarDir, "/" + relativePath, "css");
			}
		}

		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(wroFile);
		transformer.transform(source, result);
		return wroFile;
	}

	private void addGroupElement(Document doc, Element group, File tmpWarDir, String relativePath, String suffix)
	{
		String path = relativePath;
		boolean minFound = false;
		if (path.contains("."))
		{
			String currentSuffix = path.substring(path.lastIndexOf("."));
			String minSuffix = (".min" + currentSuffix).toLowerCase();
			minFound = path.toLowerCase().endsWith(minSuffix);
			if (!minFound)
			{
				//the minified version is preferred if it exists
				File file = new File(tmpWarDir, path);
				File parent = file.getParentFile();
				String[] list = parent.list(new WildcardFileFilter(file.getName().substring(0, file.getName().lastIndexOf(".") + 1) + "*", IOCase.INSENSITIVE));
				if (list != null)
				{
					for (String name : list)
					{
						if (name.toLowerCase().endsWith(minSuffix))
						{
							minFound = true;
							File f = new File(parent, name);
							path = f.getAbsolutePath().replace(tmpWarDir.getAbsolutePath(), "").replaceAll("\\\\", "/");
							break;
						}
					}
				}
			}
		}
		Attr attr;
		Element element = doc.createElement(suffix);
		group.appendChild(element);
		element.setTextContent(path);
		attr = doc.createAttribute("minimize");
		attr.setValue(Boolean.toString(exportModel.isMinimizeJsCssResources() && !minFound));
		element.setAttributeNode(attr);
	}

	/**
	 * Copy to the war the properties file containing the selected NG components and services.
	 * This is needed to optimize the references included in the index.html file.
	 * If no components and services are selected, then all references would be included in the index.
	 */
	private void copyExportedComponentsAndServicesPropertyFile(File tmpWarDir) throws ExportException
	{
		if ((exportModel.getExportedComponents() == null && exportModel.getExportedServices() == null) ||
			(exportModel.getExportedComponents().size() == componentsSpecProviderState.getWebObjectSpecifications().size() &&
				exportModel.getExportedServices().size() == NGUtils.getAllWebServiceSpecificationsThatCanBeUncheckedAtWarExport(
					servicesSpecProviderState).length))
			return;

		File exported = new File(tmpWarDir, "WEB-INF/exported_web_objects.properties");
		Properties properties = new Properties();
		StringBuilder webObjects = new StringBuilder();
		for (String component : exportModel.getExportedComponents())
		{
			webObjects.append(component + ",");
		}

		TreeSet<String> allServices = new TreeSet<String>();
		// append internal servoy services
		PackageSpecification<WebObjectSpecification> servoyservices = servicesSpecProviderState.getWebObjectSpecifications().get("servoyservices");
		if (servoyservices != null) allServices.addAll(servoyservices.getSpecifications().keySet());
		// append user services
		if (exportModel.getExportedServices() != null) allServices.addAll(exportModel.getExportedServices());
		for (String service : allServices)
		{
			webObjects.append(service + ",");
		}
		properties.put("usedWebObjects", webObjects.substring(0, webObjects.length() - 1));
		try (FileOutputStream fos = new FileOutputStream(exported))
		{
			properties.store(fos, "");
		}
		catch (Exception e)
		{
			throw new ExportException("Couldn't generate the exported_web_objects.properties file", e);
		}
	}

	/**
	 * Copy to the war all NG components and services (default and user-defined), as well as the jars required by the NGClient.
	 */
	private void copyComponentsAndServicesPlusLibs(IProgressMonitor monitor, File tmpWarDir, final File targetLibDir) throws ExportException
	{
		try
		{
			StringBuilder componentLocations = new StringBuilder();
			StringBuilder servicesLocations = new StringBuilder();

			Map<String, File> allTemplates = new HashMap<String, File>();
			List<String> excludedComponentPackages = exportModel.getExcludedComponentPackages();
			List<String> excludedServicePackages = exportModel.getExcludedServicePackages();
			ComponentResourcesExporter.copyDefaultComponentsAndServices(tmpWarDir, excludedComponentPackages, excludedServicePackages, allTemplates);

			componentLocations.append(ComponentResourcesExporter.getDefaultComponentDirectoryNames(excludedComponentPackages));
			servicesLocations.append(ComponentResourcesExporter.getDefaultServicesDirectoryNames(excludedServicePackages));

			monitor.worked(1);
			BaseNGPackageManager packageManager = ServoyModelFinder.getServoyModel().getNGPackageManager();

			List<IPackageReader> packageReaders = packageManager.getAllPackageReaders();
			for (IPackageReader packageReader : packageReaders)
			{
				File resource = packageReader.getResource();
				if (resource != null)
				{
					boolean copy = false;
					String name = packageReader.getPackageName();
					if (IPackageReader.WEB_COMPONENT.equals(packageReader.getPackageType()))
					{
						if (excludedComponentPackages == null || !excludedComponentPackages.contains(name))
						{
							componentLocations.append("/" + name + "/;");
							copy = true;
						}
					}
					else if (IPackageReader.WEB_SERVICE.equals(packageReader.getPackageType()))
					{
						if (excludedServicePackages == null || !excludedServicePackages.contains(name))
						{
							servicesLocations.append("/" + name + "/;");
							copy = true;
						}
					}
					else if (IPackageReader.WEB_LAYOUT.equals(packageReader.getPackageType()))
					{
						PackageSpecification<WebLayoutSpecification> spec = componentsSpecProviderState.getLayoutSpecifications().get(name);
						copy = spec != null && (spec.getCssClientLibrary() != null && !spec.getCssClientLibrary().isEmpty() ||
							spec.getJsClientLibrary() != null && !spec.getJsClientLibrary().isEmpty());
						if (copy) componentLocations.append("/" + name + "/;");
					}
					if (copy)
					{
						if (resource.isDirectory())
						{
							copyDir(resource, new File(tmpWarDir, name), true, allTemplates);
						}
						else
						{
							extractJar(name, resource, tmpWarDir, allTemplates);
						}
					}
				}
			}
			monitor.worked(1);

			createSpecLocationsPropertiesFile(new File(tmpWarDir, "WEB-INF/components.properties"), componentLocations.toString());
			createSpecLocationsPropertiesFile(new File(tmpWarDir, "WEB-INF/services.properties"), servicesLocations.toString());

			copyAllHtmlTemplates(tmpWarDir, allTemplates);

			copyNGLibs(targetLibDir);
			monitor.worked(1);
		}
		catch (IOException e)
		{
			throw new ExportException("Could not copy the components", e);
		}
	}

	private void copyAllHtmlTemplates(File tmpWarDir, Map<String, File> allTemplates)
	{
		File allTemplatesFile = new File(tmpWarDir, "js/servoy_alltemplates.js");

		StringBuilder allTemplatesContent = new StringBuilder();
		allTemplatesContent.append("angular.module(\"servoyalltemplates\",[]).run([\"$templateCache\", function($templateCache) {\n");

		for (String path : allTemplates.keySet())
		{
			allTemplatesContent.append("$templateCache.put(\"");
			allTemplatesContent.append(path);
			allTemplatesContent.append("\",\"");

			String htmlContent = Utils.getTXTFileContent(allTemplates.get(path));
			htmlContent = htmlContent.trim();
			htmlContent = htmlContent.replaceAll("\r", "");
			htmlContent = htmlContent.replaceAll("\n", "");
			htmlContent = htmlContent.replaceAll("\"", "\\\\\"");
			allTemplatesContent.append(htmlContent);
			allTemplatesContent.append("\");\n");
		}

		allTemplatesContent.append("}]);");
		Utils.writeTXTFile(allTemplatesFile, allTemplatesContent.toString());
	}

	/**
	 * Add all NG_LIBS to the war.
	 * @param targetLibDir
	 * @throws ExportException
	 * @throws IOException
	 */
	private void copyNGLibs(File targetLibDir) throws ExportException, IOException
	{
		if (pluginFiles.isEmpty())
		{
			String notFound = searchExportedPlugins();
			if (notFound != null) throw new ExportException(notFound + " was not found. Please specify location");
		}
		for (File file : pluginFiles)
		{
			if (file.getName().contains("servoy_ngclient"))
			{
				copyNGClientJar(file, targetLibDir);
			}
			else
			{
				copyFile(file, new File(targetLibDir, file.getName()));
			}
		}
	}

	/**
	 * Copy the servoy_ngclient jar to the libs folder in the .war.
	 * Exclude folders defined in EXCLUDE_FROM_NG_JAR.
	 * @param file
	 * @param targetLibDir
	 * @throws IOException
	 */
	private void copyNGClientJar(File file, File targetLibDir) throws IOException
	{
		File dest = new File(targetLibDir, file.getName());
		ZipInputStream zin = new ZipInputStream(new FileInputStream(file));
		ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(dest));
		byte[] buf = new byte[1024];

		ZipEntry entry = zin.getNextEntry();
		while (entry != null)
		{
			String name = entry.getName();
			boolean toBeDeleted = false;
			for (String f : EXCLUDE_FROM_NG_JAR)
			{
				if (name.startsWith(f))
				{
					toBeDeleted = true;
					break;
				}
			}
			if (!toBeDeleted)
			{
				// Add ZIP entry to output stream.
				zout.putNextEntry(new ZipEntry(name));
				// Transfer bytes from the ZIP file to the output file
				int len;
				while ((len = zin.read(buf)) > 0)
				{
					zout.write(buf, 0, len);
				}
			}
			entry = zin.getNextEntry();
		}
		// Close the streams
		zin.close();
		// Compress the files
		// Complete the ZIP file
		zout.close();
	}

	/**
	 * @param tmpWarDir
	 * @param locations
	 * @throws ExportException
	 */
	private void createSpecLocationsPropertiesFile(File file, String locations) throws ExportException
	{
		Properties properties = new Properties();
		properties.put("locations", locations);
		try (FileOutputStream fos = new FileOutputStream(file))
		{
			properties.store(fos, "");
		}
		catch (Exception e)
		{
			throw new ExportException("Couldn't generate the components.properties file", e);
		}
	}

	private void extractJar(String dirName, File file, File tmpWarDir, Map<String, File> allTemplates)
	{
		try (JarFile jarfile = new JarFile(file))
		{
			Enumeration<JarEntry> enu = jarfile.entries();
			while (enu.hasMoreElements())
			{
				String destdir = tmpWarDir + "/" + dirName;
				JarEntry je = enu.nextElement();
				File fl = new File(destdir, je.getName());
				if (!fl.exists())
				{
					fl.getParentFile().mkdirs();
					fl = new File(destdir, je.getName());
				}
				if (je.isDirectory())
				{
					continue;
				}
				try (InputStream is = jarfile.getInputStream(je); FileOutputStream fo = new FileOutputStream(fl))
				{
					while (is.available() > 0)
					{
						fo.write(is.read());
					}
				}
				if (fl.getName().endsWith(".html"))
				{
					allTemplates.put(dirName + "/" + je.getName(), fl);
				}
			}
		}
		catch (IOException e)
		{
			Debug.error("IO exception when extracting from file " + file.getAbsolutePath(), e);
		}
	}

	private File createNewWarFile() throws ExportException
	{
		File warFile = new File(exportModel.getWarFileName());
		if (warFile.exists() && !warFile.delete())
		{
			throw new ExportException("Can't delete the existing war file " + exportModel.getWarFileName());
		}

		try
		{
			if (warFile.getParentFile() != null && !warFile.getParentFile().exists()) warFile.getParentFile().mkdirs();
			if (!warFile.createNewFile())
			{
				throw new ExportException("Can't create the file " + exportModel.getWarFileName());
			}
		}
		catch (IOException e)
		{
			throw new ExportException("Can't create the file " + exportModel.getWarFileName(), e);
		}
		return warFile;
	}

	private File createTempDir() throws ExportException
	{
		File tmpDir = new File(System.getProperty("java.io.tmpdir"));

		File tmpWarDir = new File(tmpDir, "warexport" + System.currentTimeMillis() + "/");
		if (!tmpWarDir.mkdirs())
		{
			throw new ExportException("Can't create the temp dir  " + tmpWarDir);
		}
		return tmpWarDir;
	}

	protected void copyActiveSolution(IProgressMonitor monitor, File tmpWarDir) throws ExportException
	{
		try
		{
			IServoyModel servoyModel = ServoyModelFinder.getServoyModel();
			FlattenedSolution solution = servoyModel.getFlattenedSolution();
			Solution[] modules = solution.getModules();
			if (exportModel.isExportNoneActiveSolutions() && !exportModel.getNoneActiveSolutions().isEmpty())
			{
				List<String> noneActiveSolutions = exportModel.getNoneActiveSolutions();
				Solution[] copy = null;
				int start = 0;
				if (modules != null && modules.length > 0)
				{
					start = modules.length;
					copy = new Solution[start + noneActiveSolutions.size()];
					System.arraycopy(modules, 0, copy, 0, modules.length);
				}
				else
				{
					copy = new Solution[noneActiveSolutions.size()];
				}
				for (String name : noneActiveSolutions)
				{
					ServoyProject servoyProject = servoyModel.getServoyProject(name);
					if (servoyProject == null || servoyProject.getSolution() == null)
					{
						throw new ExportException("Can't export none active soluton with the name: " + name +
							" it couildn't be found in the workspace or the solution couldnt be loaded");
					}
					copy[start++] = servoyProject.getSolution();
				}
				modules = copy;
			}
			SolutionSerializer.writeRuntimeSolution(null, new File(tmpWarDir, "WEB-INF/solution.runtime"), solution.getSolution(),
				ApplicationServerRegistry.get().getDeveloperRepository(), modules);
			exportSolution(monitor, tmpWarDir.getCanonicalPath(), solution.getSolution(), false);

			File preImportFolder = new File(tmpWarDir, "WEB-INF/preImport");
			File postImportFolder = new File(tmpWarDir, "WEB-INF/postImport");
			for (ServoyProject sp : servoyModel.getModulesOfActiveProject())
			{
				File destinationFolder = null;
				if (SolutionMetaData.isPreImportHook(sp.getSolution()))
				{
					destinationFolder = preImportFolder;
				}
				else if (SolutionMetaData.isPostImportHook(sp.getSolution()))
				{
					destinationFolder = postImportFolder;
				}

				if (destinationFolder != null)
				{
					if (!destinationFolder.exists()) destinationFolder.mkdir();
					SolutionSerializer.writeRuntimeSolution(null, new File(destinationFolder, sp.getSolution().getName() + ".runtime"), sp.getSolution(),
						ApplicationServerRegistry.get().getDeveloperRepository(),
						sp.getSolution().getReferencedModulesRecursive(new HashMap<String, Solution>()).values().toArray(new Solution[0]));
				}
			}

		}
		catch (ExportException e)
		{
			throw e;
		}
		catch (Exception ex)
		{
			throw new ExportException("Cannot write the active solution in war file: " + ex.getMessage(), ex);
		}

		try
		{
			File importProperties = new File(tmpWarDir, "WEB-INF/import.properties");
			Properties prop = new Properties();
			prop.setProperty("overwriteGroups", Boolean.toString(exportModel.isOverwriteGroups()));
			prop.setProperty("allowSQLKeywords", Boolean.toString(exportModel.isAllowSQLKeywords()));
			prop.setProperty("overrideSequenceTypes", Boolean.toString(exportModel.isOverrideSequenceTypes()));
			prop.setProperty("overrideDefaultValues", Boolean.toString(exportModel.isOverrideDefaultValues()));
			prop.setProperty("insertNewI18NKeysOnly", Boolean.toString(exportModel.isInsertNewI18NKeysOnly()));
			prop.setProperty("importUserPolicy", Integer.toString(exportModel.getImportUserPolicy()));
			prop.setProperty("addUsersToAdminGroup", Boolean.toString(exportModel.isAddUsersToAdminGroup()));
			prop.setProperty("allowDataModelChange", Boolean.toString(exportModel.isAllowDataModelChanges()));
			prop.setProperty("updateSequences", Boolean.toString(exportModel.isUpdateSequences()));
			prop.setProperty("automaticallyUpgradeRepository", Boolean.toString(exportModel.isAutomaticallyUpgradeRepository()));

			try (FileWriter writer = new FileWriter(importProperties))
			{
				prop.store(writer, "import properties");
			}
		}
		catch (Exception e)
		{
			ServoyLog.logError(e);
		}
	}

	/**
	 * @param tmpWarDir
	 */
	private void exportAdminUser(File tmpWarDir)
	{
		if (exportModel.getDefaultAdminUser() != null && exportModel.getDefaultAdminPassword() != null)
		{
			try
			{
				File adminProperties = new File(tmpWarDir, "WEB-INF/admin.properties");
				Properties prop = new Properties();
				prop.setProperty("defaultAdminUser", exportModel.getDefaultAdminUser());
				prop.setProperty("defaultAdminPassword", SecuritySupport.encrypt(Settings.getInstance(), exportModel.getDefaultAdminPassword()));
				if (exportModel.isUseAsRealAdminUser())
				{
					prop.setProperty("useAsRealAdminUser", "true");
				}
				try (FileWriter writer = new FileWriter(adminProperties))
				{
					prop.store(writer, "admin properties");
				}
			}
			catch (Exception e)
			{
				ServoyLog.logError(e);
			}
		}
	}

	protected void createTomcatContextXML(File tmpWarDir)
	{
		if (!exportModel.isCreateTomcatContextXML()) return;
		try
		{
			File metaDir = new File(tmpWarDir, "META-INF");
			metaDir.mkdir();
			File contextFile = new File(tmpWarDir, "META-INF/context.xml");
			contextFile.createNewFile();
			try (FileWriter writer = new FileWriter(contextFile))
			{
				String fileContent = "<Context ";
				if (exportModel.isAntiResourceLocking()) fileContent += "antiResourceLocking=\"true\" ";
				if (exportModel.isClearReferencesStatic()) fileContent += "clearReferencesStatic=\"true\" ";
				if (exportModel.isClearReferencesStopThreads()) fileContent += "clearReferencesStopThreads=\"true\" ";
				if (exportModel.isClearReferencesStopTimerThreads()) fileContent += "clearReferencesStopTimerThreads=\"true\" ";
				fileContent += "></Context>";
				writer.write(fileContent);
			}
		}
		catch (Exception e)
		{
			ServoyLog.logError(e);
		}
	}

	private void exportSolution(IProgressMonitor monitor, String tmpWarDir, Solution activeSolution, boolean exportSolution)
		throws CoreException, ExportException
	{
		int totalDuration = IProgressMonitor.UNKNOWN;
		if (exportModel.getModulesToExport() != null) totalDuration = (int)(1.42 * exportModel.getModulesToExport().length); // make the main export be 70% of the time, leave the rest for sample data
		monitor.beginTask("Exporting solution", totalDuration);

		final IApplicationServerSingleton as = ApplicationServerRegistry.get();
		AbstractRepository rep = (AbstractRepository)as.getDeveloperRepository();

		IUserManager sm = as.getUserManager();
		EclipseExportUserChannel eeuc = new EclipseExportUserChannel(exportModel, monitor);
		EclipseExportI18NHelper eeI18NHelper = new EclipseExportI18NHelper(new WorkspaceFileAccess(ResourcesPlugin.getWorkspace()));
		IXMLExporter exporter = as.createXMLExporter(rep, sm, eeuc, Settings.getInstance(), as.getDataServer(), as.getClientId(), eeI18NHelper);

		try
		{
			ITableDefinitionsManager tableDefManager = null;
			IMetadataDefManager metadataDefManager = null;
			if (exportModel.isExportUsingDbiFileInfoOnly())
			{
				Pair<ITableDefinitionsManager, IMetadataDefManager> defManagers = TableDefinitionUtils.getTableDefinitionsFromDBI(activeSolution,
					exportModel.isExportReferencedModules(), exportModel.isExportI18NData(), exportModel.isExportAllTablesFromReferencedServers(),
					exportModel.isExportMetaData());
				if (defManagers != null)
				{
					tableDefManager = defManagers.getLeft();
					metadataDefManager = defManagers.getRight();
				}
			}
			exporter.exportSolutionToFile(activeSolution, new File(tmpWarDir, "WEB-INF/solution.servoy"), ClientVersion.getVersion(),
				ClientVersion.getReleaseNumber(), exportModel.isExportMetaData(), exportModel.isExportSampleData(), exportModel.getNumberOfSampleDataExported(),
				exportModel.isExportI18NData(), exportModel.isExportUsers(), exportModel.isExportReferencedModules(), exportModel.isProtectWithPassword(),
				tableDefManager, metadataDefManager, exportSolution, exportModel.useImportSettings() ? exportModel.getImportSettings() : null, null);

			monitor.done();
		}
		catch (RepositoryException e)
		{
			throw new ExportException(e.getMessage(), e);
		}
		catch (JSONException jsonex)
		{
			throw new ExportException("Bad JSON file structure: " + jsonex.getMessage(), jsonex);
		}
		catch (IOException ioex)
		{
			throw new ExportException("Exception getting files: " + ioex.getMessage(), ioex);
		}
	}


	/**
	 * @param tmpWarDir
	 * @throws ExportException
	 */
	private void addServoyProperties(File tmpWarDir) throws ExportException
	{
		if (exportModel.getServoyPropertiesFileName() == null)
		{
			generatePropertiesFile(tmpWarDir);
		}
		else
		{
			File sourceFile = new File(exportModel.getServoyPropertiesFileName());
			if (exportModel.allowOverwriteSocketFactoryProperties() || !exportModel.getLicenses().isEmpty() ||
				(exportModel.getUserHome() != null && exportModel.getUserHome().trim().length() > 0))
			{
				changeAndWritePropertiesFile(tmpWarDir, sourceFile);
			}
			else
			{
				copyPropertiesFileToWar(tmpWarDir, sourceFile);
			}
		}
	}

	private void copyWebXml(File tmpWarDir) throws ExportException
	{
		// copy war web.xml
		File webXMLFile = new File(tmpWarDir, "WEB-INF/web.xml");
		String name = exportModel.getWebXMLFileName();
		InputStream webXmlIS = null;
		if (name == null)
		{
			webXmlIS = WarExporter.class.getResourceAsStream("resources/web.xml");
		}
		else try
		{
			String message = exportModel.checkWebXML();
			if (message != null)
			{
				throw new ExportException(message);
			}
			webXmlIS = new FileInputStream(name);
		}
		catch (FileNotFoundException fnfe)
		{
			throw new ExportException("Can't create the web.xml file, couldn't read" + name, fnfe);
		}
		try (InputStream is = webXmlIS; BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(webXMLFile)))
		{
			copyStream(webXmlIS, bos);
		}
		catch (Exception e)
		{
			throw new ExportException("Can't create the web.xml file: " + webXMLFile.getAbsolutePath(), e);
		}
	}


	private void copyLog4jXml(File tmpWarDir) throws ExportException
	{
		// copy war log4j.xml
		File log4jXMLFile = new File(tmpWarDir, "WEB-INF/log4j.xml");
		String name = exportModel.getLog4jXMLFileName();
		InputStream logXmlIS = null;
		if (name == null)
		{
			logXmlIS = WarExporter.class.getResourceAsStream("resources/log4j.xml");
		}
		else try
		{
			String message = exportModel.checkLog4jXML();
			if (message != null)
			{
				throw new ExportException(message);
			}
			logXmlIS = new FileInputStream(name);
		}
		catch (FileNotFoundException fnfe)
		{
			throw new ExportException("Can't create the log4j.xml file, couldn't read" + name, fnfe);
		}
		try (InputStream is = logXmlIS; BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(log4jXMLFile)))
		{
			copyStream(is, bos);
		}
		catch (Exception e)
		{
			throw new ExportException("Can't create the log4j.xml file: " + log4jXMLFile.getAbsolutePath(), e);
		}
	}

	private static void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException
	{
		byte[] buffer = new byte[8096];
		int read = inputStream.read(buffer);
		while (read != -1)
		{
			outputStream.write(buffer, 0, read);
			read = inputStream.read(buffer);
		}
	}

	private void moveSlf4j(File tmpWarDir, final File targetLibDir) throws ExportException
	{
		// move the slf4j outside of the WEB-INF/lib to /lib/, its only used in the client
		File slf4j = new File(targetLibDir, "slf4j-jdk14.jar");
		copyFile(slf4j, new File(tmpWarDir, "lib/slf4j-jdk14.jar"));
		slf4j.delete();
	}

	/**
	 * @param tmpWarDir
	 * @param appServerDir
	 * @throws ExportException
	 */
	private void copyLibImages(File tmpWarDir, String appServerDir) throws ExportException
	{
		// copy lib/images dir
		File libImagesDir = new File(appServerDir, "lib/images");
		File targetLibImagesDir = new File(tmpWarDir, "lib/images");
		targetLibImagesDir.mkdirs();
		copyDir(libImagesDir, targetLibImagesDir, false);
	}

	/**
	 * @param monitor
	 * @param appServerDir
	 * @param targetLibDir
	 * @throws ExportException
	 */
	private void copyDrivers(String appServerDir, final File targetLibDir) throws ExportException
	{
		List<String> drivers = exportModel.getDrivers();
		File srcDriverDir = new File(appServerDir, "drivers");
		for (String driverFileName : drivers)
		{
			copyFile(new File(srcDriverDir, driverFileName), new File(targetLibDir, driverFileName));
		}
	}

	private File copyStandardLibs(File tmpWarDir, String appServerDir) throws ExportException
	{
		// copy lib dir (excluding images)
		final File libDir = new File(appServerDir, "lib");
		final File targetLibDir = new File(tmpWarDir, "WEB-INF/lib");
		targetLibDir.mkdirs();
		copyDir(libDir, targetLibDir, false);

//		// copy the template handler
//		copyFile(new File(appServerDir, "server/lib/template-handler.jar"), new File(targetLibDir, "template-handler.jar"));

		// delete the servlet.jar that one isn't allowed.
		new File(targetLibDir, "servlet-api.jar").delete();
		new File(targetLibDir, "jsp-api.jar").delete();
		// delete the tomcat boostrapper, also not needed in a war file
		new File(targetLibDir, "server-bootstrap.jar").delete();
		new File(targetLibDir, "tomcat-juli.jar").delete();
		new File(targetLibDir, "tim-api.jar").delete();
		return targetLibDir;
	}

	private void copyLafs(File tmpWarDir, String appServerDir) throws ExportException
	{

		// copy the lafs
		File lafSourceDir = new File(appServerDir, "lafs");
		File lafTargetDir = new File(tmpWarDir, "lafs");
		lafTargetDir.mkdirs();
		ILAFManagerInternal lafManager = ApplicationServerRegistry.get().getLafManager();
		Map<String, List<ExtensionResource>> loadedLafDefs = lafManager.getLoadedLAFDefs();
		List<String> lafs = exportModel.getLafs();
		File lafProperties = new File(lafTargetDir, "lafs.properties");
		try (Writer fw = new FileWriter(lafProperties))
		{
			Set<File> writtenFiles = new HashSet<File>();
			for (String lafName : lafs)
			{
				List<ExtensionResource> fileNames = JarManager.getExtensions(loadedLafDefs, lafName);
				ArrayList<String> files = new ArrayList<String>();
				if (fileNames != null)
				{
					for (ExtensionResource ext : fileNames)
					{
						files.add(ext.jarFileName);
					}
				}
				else
				{
					files.add(lafName);
				}
				for (String f : files)
				{
					File sourceFile = new File(lafSourceDir, f);
					copyFile(sourceFile, new File(lafTargetDir, f));
					writeFileEntry(fw, sourceFile, f, writtenFiles);
				}
			}
		}
		catch (IOException e2)
		{
			throw new ExportException("Error creating lafs properties file " + lafProperties.getAbsolutePath(), e2);
		}
	}

	private void copyPlugins(File tmpWarDir, String appServerDir) throws ExportException
	{
		// copy the plugins
		File pluginsDir = new File(tmpWarDir, "plugins");
		pluginsDir.mkdirs();
		List<String> plugins = exportModel.getPlugins();
		File pluginProperties = new File(pluginsDir, "plugins.properties");
		try (Writer fw = new FileWriter(pluginProperties))
		{
			Set<File> writtenFiles = new HashSet<File>();
			for (String plugin : plugins)
			{
				String pluginName = "plugins/" + plugin;
				File pluginFile = new File(appServerDir, pluginName);

				if (pluginFile.isDirectory())
				{
					copyDir(pluginName, pluginFile, tmpWarDir, fw, writtenFiles, true);
				}
				else
				{
					writeFileEntry(fw, pluginFile, plugin, writtenFiles);

					copyFile(pluginFile, new File(tmpWarDir, pluginName));

					copyJnlp(tmpWarDir, appServerDir, pluginName + ".jnlp", fw, writtenFiles);

					if (pluginName.toLowerCase().endsWith(".jar") || pluginName.toLowerCase().endsWith(".zip"))
					{
						String pluginLibDir = pluginName.substring(0, pluginName.length() - 4);
						File pluginLibDirFile = new File(appServerDir, pluginLibDir);
						copyDir(pluginLibDir, pluginLibDirFile, tmpWarDir, fw, writtenFiles, false);
					}
				}
			}
		}
		catch (IOException e1)
		{
			throw new ExportException("Error creating plugins dir", e1);
		}
	}

	private static void copyDir(String dirName, File dirFile, File tmpWarDir, Writer propertiesWriter, Set<File> writtenFiles, boolean recursive)
		throws ExportException, IOException
	{
		if (dirFile.exists() && dirFile.isDirectory())
		{
			Set<File> copiedFiles = copyDir(dirFile, new File(tmpWarDir, dirName), recursive);
			for (File file : copiedFiles)
			{
				String fileName = file.getAbsolutePath().replace('\\', '/');
				int index = fileName.indexOf("plugins/");
				if (index != -1)
				{
					fileName = fileName.substring(index + "plugins/".length());
				}
				writeFileEntry(propertiesWriter, file, fileName, writtenFiles);
			}
		}
	}

	private void copyBeans(File tmpWarDir, String appServerDir) throws ExportException
	{
		// copy the beans
		File beanSourceDir = new File(appServerDir, "beans");
		File beanTargetDir = new File(tmpWarDir, "beans");
		beanTargetDir.mkdirs();
		IBeanManagerInternal beanManager = ApplicationServerRegistry.get().getBeanManager();
		Map<String, List<ExtensionResource>> loadedBeanDefs = beanManager.getLoadedBeanDefs();
		List<String> beans = exportModel.getBeans();
		File beanProperties = new File(beanTargetDir, "beans.properties");
		try (Writer fw = new FileWriter(beanProperties))
		{
			Set<File> writtenFiles = new HashSet<File>();
			for (String beanName : beans)
			{
				List<ExtensionResource> fileNames = JarManager.getExtensions(loadedBeanDefs, beanName);
				if (fileNames != null)
				{
					for (ExtensionResource ext : fileNames)
					{
						File sourceFile = new File(beanSourceDir, ext.jarFileName);
						copyFile(sourceFile, new File(beanTargetDir, ext.jarFileName));
						writeFileEntry(fw, sourceFile, ext.jarFileName, writtenFiles);
					}
				}
			}
		}
		catch (IOException e2)
		{
			throw new ExportException("Error creating beans dir", e2);
		}
	}

	private void copyRootWebappFiles(File tmpWarDir, String appServerDir) throws ExportException
	{
		File webAppDir = new File(appServerDir, "server/webapps/ROOT");
		// copy first the standard webapp dir of the app server
		copyDir(webAppDir, tmpWarDir, true);

		File defaultCss = new File(tmpWarDir, "/servoy-webclient/templates/default/servoy_web_client_default.css");
		if (!defaultCss.exists())
		{
			try
			{
				defaultCss.getParentFile().mkdirs();
				String styleCSS = TemplateGenerator.getStyleCSS("servoy_web_client_default.css");
				OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(defaultCss), "utf8");
				fw.write(styleCSS);
				fw.close();
			}
			catch (Exception e)
			{
				throw new ExportException("Error default servoy css file (servoy_web_client_default.css)", e);
			}
		}
	}

	private void changeAndWritePropertiesFile(File tmpWarDir, File sourceFile) throws ExportException
	{
		try (FileInputStream fis = new FileInputStream(sourceFile);
			FileOutputStream fos = new FileOutputStream(new File(tmpWarDir, "WEB-INF/servoy.properties")))
		{
			Properties properties = new SortedProperties();
			properties.load(fis);

			if (exportModel.getUserHome() != null && exportModel.getUserHome().trim().length() > 0)
			{
				properties.setProperty(Settings.USER_HOME, exportModel.getUserHome());
			}

			if (exportModel.allowOverwriteSocketFactoryProperties())
			{
				properties.setProperty("SocketFactory.rmiServerFactory", "com.servoy.j2db.server.rmi.tunnel.ServerTunnelRMISocketFactoryFactory");
				properties.setProperty("SocketFactory.tunnelConnectionMode", "http&socket");
				if (properties.containsKey("SocketFactory.useTwoWaySocket")) properties.remove("SocketFactory.useTwoWaySocket");
			}

			if (!exportModel.getLicenses().isEmpty())
			{
				List<License> licenses = new ArrayList<>();
				Cipher desCipher = null;
				try
				{
					Cipher cipher = Cipher.getInstance("DESede"); //$NON-NLS-1$
					cipher.init(Cipher.DECRYPT_MODE, SecuritySupport.getCryptKey(null));
					desCipher = cipher;
				}
				catch (Exception e)
				{
					Debug.error("Cannot load encrypted previous export passwords", e);
				}

				Set<String> codes = new HashSet<String>();
				if (properties.get("licenseManager.numberOfLicenses") != null)
				{
					int totalLicenses = Integer.parseInt(properties.getProperty("licenseManager.numberOfLicenses"));
					for (int i = 0; i < totalLicenses; i++)
					{
						String code = properties.getProperty("license." + i + ".code", "");
						if (code.startsWith(IWarExportModel.enc_prefix)) code = exportModel.decryptPassword(desCipher, code);
						codes.add(code);
						licenses.add(
							new License(properties.getProperty("license." + i + ".company_name"), code, properties.getProperty("license." + i + ".licenses")));
					}
				}

				for (License license : exportModel.getLicenses())
				{
					if (ApplicationServerRegistry.get().checkClientLicense(license.getCompanyKey(), license.getCode(), license.getNumberOfLicenses()))
					{
						if (codes.contains(license.getCode()))
						{
							ServoyLog.logInfo("Duplicate license for license key " + license.getCode().substring(0, 4) +
								"**-******-******. Please check the servoy.properties file");
							continue;
						}
						licenses.add(license);
					}
					else
					{
						ServoyLog.logError(new Exception("The license \"" + license.getCompanyKey() + ", " + license.getCode().substring(0, 4) +
							"**-******-******," + license.getNumberOfLicenses() + "\" is not valid"));
					}
				}

				writeLicenses(properties, licenses);

			}

			properties.store(fos, "");
		}
		catch (IOException e)
		{
			throw new ExportException("Failed to overwrite properties file", e);
		}
	}

	private void copyPropertiesFileToWar(File tmpWarDir, File sourceFile) throws ExportException
	{
		File destFile = new File(tmpWarDir, "WEB-INF/servoy.properties");
		try
		{
			if (destFile.createNewFile())
			{
				try (FileInputStream fis = new FileInputStream(sourceFile);
					FileOutputStream fos = new FileOutputStream(destFile);
					FileChannel sourceChannel = fis.getChannel();
					FileChannel destinationChannel = fos.getChannel())
				{
					// Copy source file to destination file
					destinationChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
				}
			}
		}
		catch (IOException e)
		{
			throw new ExportException("Couldn't copy the servoy properties file", e);
		}
	}

	private void generatePropertiesFile(File tmpWarDir) throws ExportException
	{
		// create the (sorted) properties file.
		Properties properties = new SortedProperties();
		properties.setProperty("SocketFactory.rmiServerFactory", "com.servoy.j2db.server.rmi.tunnel.ServerTunnelRMISocketFactoryFactory");
		properties.setProperty("SocketFactory.tunnelConnectionMode", "http&socket");
		properties.setProperty("SocketFactory.compress", "true");
		properties.setProperty("java.rmi.server.hostname", "127.0.0.1");

		// TODO ask for a keystore?
		properties.setProperty("SocketFactory.useSSL", "true");
		properties.setProperty("SocketFactory.tunnelUseSSLForHttp", "false");
		//{ "SocketFactory.SSLKeystorePath", "", "The SSL keystore path on the server", "text" }, //
		//{ "SocketFactory.SSLKeystorePassphrase", "", "The SSL passphrase to access the keystore", "password" }, //

//		properties.setProperty("servoy.use.client.timezone", "true");

		// TODO ask for all kinds of other stuff like branding?
		properties.setProperty("servoy.server.start.rmi", Boolean.toString(exportModel.getStartRMI()));
		properties.setProperty("servoy.rmiStartPort", exportModel.getStartRMIPort());


		if (exportModel.getUserHome() != null && exportModel.getUserHome().trim().length() > 0)
		{
			properties.setProperty(Settings.USER_HOME, exportModel.getUserHome());
		}

		if (!exportModel.getLicenses().isEmpty())
		{
			writeLicenses(properties, exportModel.getLicenses());
		}
		String maxSeqLength = Settings.getInstance().getProperty("ServerManager.databasesequence.maxlength");
		if (maxSeqLength != null)
		{
			properties.setProperty("ServerManager.databasesequence.maxlength", maxSeqLength);
		}
		// store the servers
		SortedSet<String> selectedServerNames = exportModel.getSelectedServerNames();
		properties.setProperty("ServerManager.numberOfServers", Integer.toString(selectedServerNames.size()));
		int i = 0;
		for (String serverName : selectedServerNames)
		{
			ServerConfiguration sc = exportModel.getServerConfiguration(serverName);

			properties.put("server." + i + ".serverName", sc.getName());
			properties.put("server." + i + ".userName", sc.getUserName());
			String password = sc.getPassword();
			try
			{
				password = IWarExportModel.enc_prefix + SecuritySupport.encrypt(Settings.getInstance(), password);
			}
			catch (Exception e)
			{
				Debug.error("Could not encrypt password for sever " + sc.getName(), e);
			}
			properties.put("server." + i + ".password", password);
			properties.put("server." + i + ".URL", sc.getServerUrl());
//			Map<String, String> connectionProperties = sc.getConnectionProperties();
//			if (connectionProperties == null)
//			{
//				Settings.removePrefixedProperties(properties, "server." + i + ".property.");
//			}
//			else
//			{
//				for (Entry<String, String> entry : connectionProperties.entrySet())
//				{
//					properties.put("server." + i + ".property." + entry.getKey(), entry.getValue());
//				}
//			}
			properties.put("server." + i + ".driver", sc.getDriver());
			properties.put("server." + i + ".skipSysTables", "" + sc.isSkipSysTables());
			properties.put("server." + i + ".queryProcedures", "" + sc.isQueryProcedures());
			properties.put("server." + i + ".prefixTables", "" + sc.isPrefixTables());
			String catalog = sc.getCatalog();
			if (catalog == null)
			{
				catalog = "<none>";
			}
			else if (catalog.trim().length() == 0)
			{
				catalog = "<empty>";
			}
			properties.put("server." + i + ".catalog", catalog);
			String schema = sc.getSchema();
			if (schema == null)
			{
				schema = "<none>";
			}
			else if (schema.trim().length() == 0)
			{
				schema = "<empty>";
			}
			properties.put("server." + i + ".schema", schema);
			properties.put("server." + i + ".maxConnectionsActive", String.valueOf(sc.getMaxActive()));
			properties.put("server." + i + ".maxConnectionsIdle", String.valueOf(sc.getMaxIdle()));
			properties.put("server." + i + ".maxPreparedStatementsIdle", String.valueOf(sc.getMaxPreparedStatementsIdle()));
			properties.put("server." + i + ".connectionValidationType", String.valueOf(sc.getConnectionValidationType()));
			if (sc.getValidationQuery() != null)
			{
				properties.put("server." + i + ".validationQuery", sc.getValidationQuery());
			}
			if (sc.getDataModelCloneFrom() != null && !"".equals(sc.getDataModelCloneFrom()))
			{
				properties.put("server." + i + ".dataModelCloneFrom", sc.getDataModelCloneFrom());
			}
			properties.put("server." + i + ".enabled", Boolean.toString(true));
//			if (sc.getDialectClass() != null)
//			{
//				properties.put("server." + i + ".dialect", sc.getDialectClass());
//			}
//			else
//			{
//				properties.remove("server." + i + ".dialect");
//			}
			i++;
		}

		try (FileOutputStream fos = new FileOutputStream(new File(tmpWarDir, "WEB-INF/servoy.properties")))
		{
			properties.store(fos, "");
		}
		catch (FileNotFoundException fileNotFoundException)
		{
			throw new ExportException("Couldn't find file " + tmpWarDir.getAbsolutePath() + "/WEB-INF/servoy.properties", fileNotFoundException);
		}
		catch (IOException e)
		{
			throw new ExportException("Couldn't generate the properties file", e);
		}
	}

	private void writeLicenses(Properties properties, Collection<License> licenses)
	{
		int i = 0;
		//THE FOLLOWING PROPERTY NAMES MUST BE THE SAME AS IN LicenseManager
		properties.setProperty("licenseManager.numberOfLicenses", Integer.toString(exportModel.getLicenses().size()));
		for (License license : licenses)
		{
			properties.setProperty("license." + i + ".company_name", license.getCompanyKey());
			properties.setProperty("license." + i + ".licenses", license.getNumberOfLicenses());
			properties.setProperty("license." + i + ".product", "0");//client
			try
			{
				properties.setProperty("license." + i + ".code",
					IWarExportModel.enc_prefix + SecuritySupport.encrypt(Settings.getInstance(), license.getCode()));
			}
			catch (Exception e)
			{
				Debug.error("Could not encrypt license key.", e);
			}
			i++;
		}
	}

	private static void writeFileEntry(Writer fw, File file, String name, Set<File> writtenFiles) throws IOException
	{

		if (writtenFiles.add(file) && file.exists())
		{
			fw.write(name);
			fw.write('=');
			fw.write(Long.toString(file.lastModified()));
			fw.write(':');
			fw.write(Long.toString(file.length()));
			fw.write('\n');
		}
	}

	private void zipDirectory(File directory, File zip) throws ExportException
	{
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip)))
		{
			zip(directory, directory, zos);
		}
		catch (Exception e)
		{
			throw new ExportException("Can't create the war file " + zip, e);
		}
	}

	private void zip(File directory, File base, ZipOutputStream zos) throws IOException
	{
		File[] files = directory.listFiles();
		byte[] buffer = new byte[8192];
		int read = 0;
		for (File file : files)
		{
			// skip the WRO4J_RUNNNER if somehow it couldn't be deleted.
			if (file.getName().equals(WRO4J_RUNNER) || file.getName().equals("wro.xml")) continue;
			if (file.isDirectory())
			{
				zip(file, base, zos);
			}
			else
			{
				FileInputStream in = new FileInputStream(file);
				ZipEntry entry = new ZipEntry(file.getPath().substring(base.getPath().length() + 1).replace('\\', '/'));
				zos.putNextEntry(entry);
				while (-1 != (read = in.read(buffer)))
				{
					zos.write(buffer, 0, read);
				}
				zos.closeEntry();
				in.close();
			}
		}
	}

	private void copyJnlp(File tmpWarDir, String appServerDir, String pluginJnlpName, Writer fw, Set<File> writtenFiles) throws ExportException, IOException
	{
		if (pluginJnlpName.startsWith("/servoy-client/"))
		{
			pluginJnlpName = pluginJnlpName.substring(15);
		}
		File pluginJarJnlpFile = new File(appServerDir, pluginJnlpName);
		if (pluginJarJnlpFile.exists())
		{
			copyFile(pluginJarJnlpFile, new File(tmpWarDir, pluginJnlpName));
			// parse the jnlp and copy all the referenced jars over.
			Document document = getDocument(pluginJarJnlpFile);
			if (document != null)
			{
				List<String> jarNames = new ArrayList<String>();
				List<String> jnlpNames = new ArrayList<String>();
				parseJarNames(document.getChildNodes(), jarNames, jnlpNames);

				for (String jarName : jarNames)
				{
					// ignore everything copied from lib, those are moved to WEB-INF/lib later on
					if (jarName.startsWith("/lib/")) continue;
					File jarFile = new File(appServerDir, jarName);
					File jarTargetFile = new File(tmpWarDir, jarName);
					jarTargetFile.getParentFile().mkdirs();
					copyFile(jarFile, jarTargetFile);
					int index = jarName.indexOf("plugins/");
					if (index != -1)
					{
						jarName = jarName.substring(index + "plugins/".length());
					}
					writeFileEntry(fw, jarFile, jarName, writtenFiles);
				}

				for (String jnlpName : jnlpNames)
				{
					copyJnlp(tmpWarDir, appServerDir, jnlpName, fw, writtenFiles);
				}
			}
			else
			{
				Debug.error("jnlp file " + pluginJarJnlpFile + " couldn't be parsed, nothing copied");
			}
		}
	}

	private void parseJarNames(NodeList childNodes, List<String> jarNames, List<String> jnlpNames)
	{
		for (int i = 0; i < childNodes.getLength(); i++)
		{
			Node node = childNodes.item(i);
			if (node.getNodeName().equalsIgnoreCase("jar"))
			{
				NamedNodeMap attributes = node.getAttributes();
				Node href = attributes.getNamedItem("href");
				if (href != null)
				{
					jarNames.add(href.getNodeValue());
				}
			}
			else if (node.getNodeName().equalsIgnoreCase("extension"))
			{
				NamedNodeMap attributes = node.getAttributes();
				Node href = attributes.getNamedItem("href");
				if (href != null)
				{
					jnlpNames.add(href.getNodeValue());
				}
			}
			parseJarNames(node.getChildNodes(), jarNames, jnlpNames);
		}
	}

	private Document getDocument(File jnlpFile)
	{
		try
		{
			String contents = Utils.getTXTFileContent(jnlpFile);
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			return docBuilder.parse(new InputSource(new StringReader(contents)));
		}
		catch (Exception e)
		{
			Debug.error("Error creating parsing the jnlp file: " + jnlpFile, e);
		}
		return null;
	}

	private static void deleteDirectory(File path)
	{
		if (path.exists())
		{
			// always call delete on exit so it will be tried to delete again later on
			// delete on exit uses reversed order so you have to call it first for the dir then the files.
			path.deleteOnExit();
			File[] files = path.listFiles();
			for (File file : files)
			{
				if (file.isDirectory())
				{
					deleteDirectory(file);
				}
				else
				{
					if (!file.delete())
					{
						file.deleteOnExit();
					}
				}
			}
		}
		path.delete();
	}

	private static Set<File> copyDir(File sourceDir, File destDir, boolean recusive, Map<String, File> allTemplates) throws ExportException
	{
		Set<File> writtenFiles = new HashSet<File>();
		copyDir(sourceDir, destDir, recusive, writtenFiles, allTemplates);
		return writtenFiles;
	}

	private static Set<File> copyDir(File sourceDir, File destDir, boolean recusive) throws ExportException
	{
		return copyDir(sourceDir, destDir, recusive, null);
	}

	private static void copyDir(File sourceDir, File destDir, boolean recusive, Set<File> writtenFiles, Map<String, File> allTemplates) throws ExportException
	{
		if (!destDir.exists() && !destDir.mkdirs()) throw new ExportException("Can't create destination dir: " + destDir);
		File[] listFiles = sourceDir.listFiles();
		if (listFiles == null) return;
		for (File file : listFiles)
		{
			if (file.isDirectory())
			{
				if (recusive) copyDir(file, new File(destDir, file.getName()), recusive, writtenFiles, allTemplates);
			}
			else
			{
				File newFile = new File(destDir, file.getName());
				copyFile(file, newFile);
				if (allTemplates != null && newFile.getName().endsWith(".html"))
				{
					String path = newFile.getPath();
					path = path.replace('\\', '/');
					path = path.substring(path.indexOf("/warexport") + 1);
					path = path.substring(path.indexOf("/") + 1);
					allTemplates.put(path, newFile);
				}
				writtenFiles.add(file);
			}
		}
	}

	private static void copyFile(File sourceFile, File destFile) throws ExportException
	{
		if (!sourceFile.exists())
		{
			return;
		}
		try
		{
			if (!destFile.getParentFile().exists())
			{
				destFile.getParentFile().mkdirs();
			}
			if (!destFile.exists())
			{
				destFile.createNewFile();
			}
			try (FileInputStream fis = new FileInputStream(sourceFile))
			{
				String compileLessWithNashorn = null;
				if (sourceFile.getName().endsWith(".less") && (compileLessWithNashorn = ResourceProvider.compileLessWithNashorn(fis)) != null)
				{
					File compiledLessFile = destFile;
					PrintWriter printWriter = new PrintWriter(compiledLessFile);
					printWriter.println(compileLessWithNashorn);
					printWriter.close();
				}
				else
				{
					try (FileChannel source = fis.getChannel();
						FileOutputStream fos = new FileOutputStream(destFile);
						FileChannel destination = fos.getChannel())
					{
						if (destination != null && source != null)
						{
							destination.transferFrom(source, 0, source.size());
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			throw new ExportException("Can't copy file from " + sourceFile + " to " + destFile, e);
		}

	}

	/**
	 * Check if all NG_LIBS can be found in the specified plugin locations.
	 * @return message to add path to the missing jar
	 */
	public String searchExportedPlugins()
	{
		pluginFiles = new HashSet<File>();
		List<String> pluginLocations = new ArrayList<String>();
		File eclipseParent = null;
		File userDir = new File(System.getProperty("user.dir"));
		if (System.getProperty("eclipse.home.location") != null)
		{
			eclipseParent = new File(URI.create(System.getProperty("eclipse.home.location").replaceAll(" ", "%20")));
			if (eclipseParent.exists())
			{
				//first check the plugins folder of eclipse home
				pluginLocations.add(new File(eclipseParent, "/plugins").getAbsolutePath().toString());
			}
		}
		pluginLocations.addAll(exportModel.getPluginLocations());
		for (String libName : NG_LIBS)
		{
			int i = 0;
			boolean found = false;
			while (!found && i < pluginLocations.size())
			{
				File pluginLocation = new File(pluginLocations.get(i));
				if (!pluginLocation.exists())
				{
					if (!pluginLocation.isAbsolute() && !pluginLocation.exists())
					{
						pluginLocation = new File(userDir, pluginLocations.get(i));
					}
					if (!pluginLocation.exists())
					{
						System.err.println("Trying userDir " + userDir + " as parent for " + pluginLocations.get(i) + " but is not found");
					}
				}

				if (!pluginLocation.isDirectory())
				{
					System.err.println(pluginLocation.getAbsolutePath() + " is not a directory.");
					i++;
					continue;
				}

				Collection<File> libs = FileUtils.listFiles(pluginLocation, new WildcardFileFilter(libName), TrueFileFilter.INSTANCE);
				Iterator<File> iterator = libs.iterator();
				if (libs != null && libs.size() > 0)
				{
					File f = iterator.next();
					if (libs.size() > 1)
					{
						//in this case we need to copy the newest
						List<File> sortedLibs = new ArrayList<File>(libs);
						Collections.sort(sortedLibs, new Comparator<File>()
						{
							@Override
							public int compare(File file0, File file1)
							{
								return Long.compare(file0.lastModified(), file1.lastModified());
							}
						});
						f = sortedLibs.get(sortedLibs.size() - 1);
						ServoyLog.logInfo("WAR EXPORT: More versions of lib " + libName + " found, will copy " + f.getAbsolutePath() + " to the war file.");
					}
					pluginFiles.add(f);
					found = true;
					break;
				}
				i++;
			}
			if (!found) return libName;
		}
		return null;
	}

	private void createDeployPropertiesFile(File tmpWarDir) throws ExportException
	{
		File deployPropertiesFile = new File(tmpWarDir, "WEB-INF/deploy.properties");
		Properties properties = new Properties();
		properties.put("isOverwriteDeployedDBServerProperties", String.valueOf(exportModel.isOverwriteDeployedDBServerProperties()));
		properties.put("isOverwriteDeployedServoyProperties", String.valueOf(exportModel.isOverwriteDeployedServoyProperties()));
		try (FileOutputStream fos = new FileOutputStream(deployPropertiesFile))
		{
			properties.store(fos, "");
		}
		catch (Exception e)
		{
			throw new ExportException("Couldn't generate the deploy.properties file", e);
		}
	}
}
