/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2021 Servoy BV

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

package com.servoy.eclipse.ngclient.ui;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.CssLib;
import org.sablo.specification.CssLibSet;
import org.sablo.specification.NG2Config;
import org.sablo.specification.Package.DirPackageReader;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.Package.ZipPackageReader;
import org.sablo.specification.PackageSpecification;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.SpecProviderState;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebLayoutSpecification;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.WebServiceSpecProvider;
import org.sablo.websocket.impl.ClientService;

import com.servoy.eclipse.model.ING2WarExportModel;
import com.servoy.eclipse.model.ServoyModelFinder;
import com.servoy.eclipse.model.ngpackages.ILoadedNGPackagesListener;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.model.war.exporter.IWarExportModel;
import com.servoy.eclipse.ngclient.ui.utils.ZipUtils;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.Utils;

/**
 * @author jcompager
 * @since 2021.06
 */
public class WebPackagesListener implements ILoadedNGPackagesListener
{
	// list of packages that are in node/projects folder
	private static final String[] defaultPackages = new String[] { "@servoy/servoydefault", "@servoy/dialogs", "@servoy/ngclientutils", "@servoy/window" };
	private static final Map<String, String> NG1MAPPING = new HashMap<>();
	static
	{
		Arrays.sort(defaultPackages);
		NG1MAPPING.put("@servoy/ngclientutils", "servoy_ng_only_services");
		NG1MAPPING.put("@servoy/dialogs", "servoydefaultservices");
		NG1MAPPING.put("@servoy/servoydefault", "servoydefault");
	}

	/**
	 * @author jcompanger
	 * @since 2021.06
	 */
	private static final class PackageCheckerJob extends Job
	{
		private final IWarExportModel warExportModel;

		/**
		 * @param name
		 */
		private PackageCheckerJob(String name, IWarExportModel warExportModel)
		{
			super(name);
			this.warExportModel = warExportModel;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor)
		{
			StringOutputStream console = Activator.getInstance().getConsole().outputStream();
			try
			{
				scheduled.incrementAndGet();
				long time = System.currentTimeMillis();
				writeConsole(console, "---- Starting Titanium NGClient source check (" + DateTimeFormatter.ISO_LOCAL_TIME.format(LocalTime.now()) + ")");
				File projectFolder = Activator.getInstance().getProjectFolder();
				// modules and css of the components those are based on the Packages itself
				CssLibSet cssLibs = new CssLibSet();
				Set<String> packageToInstall = new HashSet<>();
				Set<String> assetsToAdd = new HashSet<>();
				Map<String, Pair<WebLayoutSpecification, String>> structureTagNames = new HashMap<>();
				// service are based just on all service specifications
				Map<WebObjectSpecification, IPackageReader> ng2Services = new TreeMap<>((spec1, spec2) -> spec1.getName().compareTo(spec2.getName()));
				SpecProviderState serviceProviderState = WebServiceSpecProvider.getSpecProviderState();
				WebObjectSpecification[] allServices = serviceProviderState.getAllWebComponentSpecifications();
				for (WebObjectSpecification webObjectSpecification : allServices)
				{
					if (this.warExportModel != null && !this.warExportModel.getExportedPackages().contains(webObjectSpecification.getPackageName()))
					{
						continue;
					}
					Set<CssLib> libs = webObjectSpecification.getNG2Config().getDependencies().getCssLibrary();
					if (libs != null)
					{
						cssLibs.addAll(libs);
					}
					List<String> assets = webObjectSpecification.getNG2Config().getAssets().getAssetsList();
					if (assets != null)
					{
						assetsToAdd.addAll(assets);
					}
					if (webObjectSpecification.getNG2Config().getPackageName() != null)
					{
						if (isDefaultPackageEnabled(webObjectSpecification.getNG2Config().getPackageName()))
						{
							IPackageReader packageReader = serviceProviderState
								.getPackageReader(webObjectSpecification.getPackageName());
							ng2Services.put(webObjectSpecification, packageReader);
						}
					}
				}

				TreeMap<PackageSpecification< ? extends WebObjectSpecification>, IPackageReader> componentPackageSpecToReader = new TreeMap<>(
					(spec1, spec2) -> spec1.getPackageName().compareTo(spec2.getPackageName()));
				SpecProviderState specProviderState = WebComponentSpecProvider.getSpecProviderState();
				for (PackageSpecification<WebObjectSpecification> entry : specProviderState.getWebObjectSpecifications().values())
				{
					if (this.warExportModel != null && !this.warExportModel.getExportedPackages().contains(entry.getPackageName()))
					{
						continue;
					}

					String module = entry.getNg2Module();
					String packageName = entry.getNpmPackageName();
					if (!Utils.stringIsEmpty(module) && !Utils.stringIsEmpty(packageName))
					{
						IPackageReader packageReader = specProviderState.getPackageReader(entry.getPackageName());
						componentPackageSpecToReader.put(entry, packageReader);
					}

					Set<CssLib> libs = entry.getNg2CssLibrary();
					if (libs != null)
					{
						cssLibs.addAll(libs);
					}
				}
				for (PackageSpecification<WebLayoutSpecification> entry : specProviderState.getLayoutSpecifications().values())
				{
					if (this.warExportModel != null && !this.warExportModel.getExportedPackages().contains(entry.getPackageName()))
					{
						continue;
					}

					String module = entry.getNg2Module();
					String packageName = entry.getNpmPackageName();
					if (!Utils.stringIsEmpty(module) && !Utils.stringIsEmpty(packageName))
					{
						IPackageReader packageReader = specProviderState.getPackageReader(entry.getPackageName());
						componentPackageSpecToReader.put(entry, packageReader);
					}

					Set<CssLib> libs = entry.getNg2CssLibrary();
					if (libs != null)
					{
						cssLibs.addAll(libs);
					}

					Map<String, WebLayoutSpecification> specifications = entry.getSpecifications();
					specifications.values().forEach(spec -> {
						String tagName = null;
						PropertyDescription tagType = spec.getProperty("tagType");
						if (tagType != null && tagType.getDefaultValue() != null && !"div".equals(tagType.getDefaultValue()))
						{
							tagName = (String)tagType.getDefaultValue();
							structureTagNames.put(tagName, new Pair<WebLayoutSpecification, String>(spec, tagName));
						}
						List<String> directives = spec.getDirectives();
						if (directives.size() > 0)
						{
							if (tagName == null) tagName = "div";
							else structureTagNames.remove(tagName);
							structureTagNames.put(spec.getName(), new Pair<WebLayoutSpecification, String>(spec, tagName));
						}
					});
				}


				boolean sourceChanged = false;
				if (ng2Services.size() > 0 || componentPackageSpecToReader.size() > 0)
				{
					try
					{
						File packageJson = new File(projectFolder, "package.json");
						String json = FileUtils.readFileToString(packageJson, "UTF-8");
						JSONObject jsonObject = new JSONObject(json);
						JSONObject dependencies = jsonObject.optJSONObject("dependencies");
						ng2Services.entrySet().forEach(entry -> {
							WebObjectSpecification spec = entry.getKey();
							NG2Config ng2Config = spec.getNG2Config();
							String packageName = ng2Config.getPackageName();
							IPackageReader packageReader = entry.getValue();
							String entryPoint = ng2Config.getEntryPoint();
							String pck = checkPackage(dependencies, packageName, packageReader, entryPoint, console);
							if (pck != null)
							{
								writeConsole(console, "need to install package " + pck);
								packageToInstall.add(pck);
							}
						});

						componentPackageSpecToReader.entrySet().forEach(entry -> {
							PackageSpecification< ? extends WebObjectSpecification> spec = entry.getKey();
							String packageName = spec.getNpmPackageName();
							IPackageReader packageReader = entry.getValue();
							String entryPoint = spec.getEntryPoint();
							String pck = checkPackage(dependencies, packageName, packageReader, entryPoint, console);
							if (pck != null)
							{
								packageToInstall.add(pck);
							}
						});
					}
					catch (IOException e)
					{
						ServoyLog.logError(e);
					}
				}

				// adjust the allservices.sevice.ts
				try
				{
					StringBuilder imports = new StringBuilder("// generated imports start\n");
					StringBuilder services = new StringBuilder("// generated services start\n");
					StringBuilder providers = new StringBuilder("// generated providers start\n");
					StringBuilder modules = new StringBuilder("// generated modules start\n");
					ng2Services.keySet().forEach(service -> {
						if (service.getNG2Config().getServiceName() != null)
						{
							if (this.warExportModel != null && !this.warExportModel.getExportedServices().contains(service.getName()))
							{
								return;
							}

							String moduleName = service.getNG2Config().getModuleName();
							// import the service
							imports.append("import { ");
							String serviceName = service.getNG2Config().getServiceName();
							imports.append(serviceName);
							// add it to the modules
							if (moduleName != null && modules.indexOf(moduleName + ',') == -1)
							{
								imports.append(',');
								imports.append(moduleName);
								modules.append(moduleName);
								modules.append(",\n");
							}
							imports.append(" } from '");
							imports.append(service.getNG2Config().getPackageName());
							imports.append("';\n");
							// add it to the service declarations in the constructor
							services.append("private ");
							services.append(ClientService.convertToJSName(service.getName()));
							services.append(": ");
							services.append(serviceName);
							services.append(",\n");
							// add it to the providers (if it is not a module)
							if (!"aggridservice".equals(service.getPackageName()) && moduleName == null)
							{
								providers.append(serviceName);
								providers.append(",\n");
							}
						}
					});

					imports.append("// generated imports end");
					services.append("// generated services end");
					providers.append("// generated providers end");
					modules.append("// generated modules end");

					String content = FileUtils.readFileToString(new File(projectFolder, "src/ngclient/allservices.service.ts"), "UTF-8");
					String old = content;
					content = replace(content, "// generated imports start", "// generated imports end", imports);
					content = replace(content, "// generated services start", "// generated services end", services);
					content = replace(content, "// generated providers start", "// generated providers end", providers);
					content = replace(content, "// generated modules start", "// generated modules end", modules);
					if (!old.equals(content))
					{
						sourceChanged = true;
						writeConsole(console, "services ts file changed");
						FileUtils.writeStringToFile(new File(projectFolder, "src/ngclient/allservices.service.ts"), content, "UTF-8");
					}
				}
				catch (IOException e)
				{
					ServoyLog.logError(e);
				}

				for (WebObjectSpecification spec : WebComponentSpecProvider.getSpecProviderState().getAllWebComponentSpecifications())
				{
					if (warExportModel == null || warExportModel.getExportedComponents().contains(spec.getName()))
					{
						Set<CssLib> libs = spec.getNG2Config().getDependencies().getCssLibrary();
						if (libs != null)
						{
							cssLibs.addAll(libs);
						}
						List<String> assets = spec.getNG2Config().getAssets().getAssetsList();
						if (assets != null)
						{
							assetsToAdd.addAll(assets);
						}
					}
				}

				ComponentTemplateGenerator generator = new ComponentTemplateGenerator();
				Pair<StringBuilder, StringBuilder> componentTemplates = generator.generateHTMLTemplate(warExportModel);
				try
				{
					// adjust component templates
					String content = FileUtils.readFileToString(new File(projectFolder, "src/ngclient/form/form_component.component.ts"), "UTF-8");
					String editorContent = FileUtils.readFileToString(new File(projectFolder, "src/designer/designform_component.component.ts"), "UTF-8");
					String old = content;
					String oldEditor = editorContent;
					content = replace(content, "<!-- component template generate start -->", "<!-- component template generate end -->",
						componentTemplates.getLeft());
					content = replace(content, "// component viewchild template generate start", "// component viewchild template generate end",
						componentTemplates.getRight());
					editorContent = replace(editorContent, "<!-- component template generate start -->", "<!-- component template generate end -->",
						componentTemplates.getLeft());
					editorContent = replace(editorContent, "// component viewchild template generate start", "// component viewchild template generate end",
						componentTemplates.getRight());
					// add structure templates
					if (structureTagNames.size() > 0)
					{
						LayoutTemplates generateStructureTemplate = generateStructureTemplate(structureTagNames);
						content = replace(content, "<!-- structure template generate start -->", "<!-- structure template generate end -->",
							generateStructureTemplate.getFormComponentTemplate());
						content = replace(content, "// structure viewchild template generate start", "// structure viewchild template generate end",
							generateStructureTemplate.getViewChilds());

						editorContent = replace(editorContent, "<!-- structure template generate start -->", "<!-- structure template generate end -->",
							generateStructureTemplate.getFormComponentTemplate());
						editorContent = replace(editorContent, "// structure viewchild template generate start", "// structure viewchild template generate end",
							generateStructureTemplate.getViewChilds());

						String lfc = FileUtils.readFileToString(new File(projectFolder, "src/servoycore/listformcomponent/listformcomponent.ts"), "UTF-8");
						String oldLFC = lfc;
						lfc = replace(lfc, "<!-- structure template generate start -->", "<!-- structure template generate end -->",
							generateStructureTemplate.getLFFormComponentTemplate());
						lfc = replace(lfc, "// structure viewchild template generate start", "// structure viewchild template generate end",
							generateStructureTemplate.getViewChilds());
						if (!oldLFC.equals(lfc))
						{
							sourceChanged = true;
							writeConsole(console, "LFC components ts file changed");
							FileUtils.writeStringToFile(new File(projectFolder, "src/servoycore/listformcomponent/listformcomponent.ts"), lfc, "UTF-8");
						}

					}
					if (!old.equals(content))
					{
						sourceChanged = true;
						writeConsole(console, "components ts file changed");
						FileUtils.writeStringToFile(new File(projectFolder, "src/ngclient/form/form_component.component.ts"), content, "UTF-8");
					}

					if (!oldEditor.equals(editorContent))
					{
						sourceChanged = true;
						writeConsole(console, "editor ts file changed");
						FileUtils.writeStringToFile(new File(projectFolder, "src/designer/designform_component.component.ts"), editorContent, "UTF-8");
					}

				}
				catch (IOException e1)
				{
					ServoyLog.logError(e1);
				}

				try
				{


					// generate the all components.module.ts
					StringBuilder allComponentsModule = new StringBuilder(256);
					allComponentsModule.append("import { NgModule } from '@angular/core';\n");
					componentPackageSpecToReader.keySet().forEach(spec -> {
						allComponentsModule.append("import { ");
						allComponentsModule.append(spec.getNg2Module());
						allComponentsModule.append(" } from '");
						allComponentsModule.append(spec.getNpmPackageName());
						allComponentsModule.append("';\n");
					});

					allComponentsModule.append("@NgModule({\n imports: [\n");
					componentPackageSpecToReader.keySet().forEach(spec -> {
						allComponentsModule.append(spec.getNg2Module());
						allComponentsModule.append(",\n");
					});

					allComponentsModule.append(" ],\n exports: [\n");
					componentPackageSpecToReader.keySet().forEach(spec -> {
						allComponentsModule.append(spec.getNg2Module());
						allComponentsModule.append(",\n");
					});
					allComponentsModule.append(" ]\n})\nexport class AllComponentsModule { }\n");
					String current = allComponentsModule.toString();
					String content = FileUtils.readFileToString(new File(projectFolder, "src/ngclient/allcomponents.module.ts"), "UTF-8");

					if (!current.equals(content))
					{
						sourceChanged = true;
						writeConsole(console, "component modules  ts file changed");
						FileUtils.writeStringToFile(new File(projectFolder, "src/ngclient/allcomponents.module.ts"), current, "UTF-8");
					}
				}
				catch (IOException e1)
				{
					ServoyLog.logError(e1);
				}

				try
				{
					/* component/services imports */
					if (cssLibs.size() > -0)
					{
						String content = FileUtils.readFileToString(new File(projectFolder, "src/styles.css"), "UTF-8");
						int index = content.indexOf("/* component/services imports end */");
						StringBuilder sb = new StringBuilder();
						cssLibs.forEach(lib -> sb.append("@import \"").append(lib.getUrl()).append("\";\n"));
						String imports = sb.toString();
						if (!imports.equals(content.substring(0, index)))
						{
							if (index > 0)
							{
								content = content.substring(index);
							}
							content = imports + content;
							sourceChanged = true;
							writeConsole(console, "Styles source changed");
							FileUtils.writeStringToFile(new File(projectFolder, "src/styles.css"), content, "UTF-8");
						}
					}
				}
				catch (IOException e)
				{
					ServoyLog.logError(e);
				}
				try
				{
					if (assetsToAdd.size() > 0)
					{
						File angularJSON = new File(projectFolder, "angular.json");
						String angularJSONContents = FileUtils.readFileToString(angularJSON, "UTF8");
						JSONObject json = new JSONObject(angularJSONContents);
						JSONArray assets = json.getJSONObject("projects").getJSONObject("ngclient2").getJSONObject("architect").getJSONObject("build")
							.getJSONObject("options").getJSONArray("assets");
						boolean[] assetsChanged = new boolean[] { false };
						assetsToAdd.forEach((asset) -> {
							boolean[] assetFound = new boolean[] { false };
							assets.forEach(existingAsset -> {
								if (existingAsset.toString().equals(asset))
								{
									assetFound[0] = true;
								}
							});
							if (!assetFound[0])
							{
								assetsChanged[0] = true;
								if (asset.startsWith("{") && asset.endsWith("}"))
								{
									try
									{
										JSONObject assetJSONObject = new JSONObject(asset);
										assets.put(assetJSONObject);
									}
									catch (JSONException ex)
									{
										ServoyLog.logError("Can't add asset object to angular.json: " + asset, ex);
									}
								}
								else
								{
									assets.put(asset);
								}
							}
						});
						if (assetsChanged[0])
						{
							writeConsole(console, "Assets source changed");
							sourceChanged = true;
							FileUtils.write(angularJSON, json.toString(1), "UTF8", false);
						}
					}
				}
				catch (IOException e)
				{
					ServoyLog.logError(e);
				}
				if (packageToInstall.size() > 0 || sourceChanged || !new File(projectFolder, "dist").exists() || cleanInstall.get() || warExportModel != null)
				{
					// first exeuted npm install with all the packages.
					// only execute this if a source is changed (should always happens the first time)
					// or if there are really packages to install.
					List<String> command = new ArrayList<>();
					command.add("install");
					packageToInstall.forEach(packageName -> command.add(packageName));
					command.add("--legacy-peer-deps");
					RunNPMCommand npmCommand = Activator.getInstance().createNPMCommand(command);
					try
					{
						npmCommand.runCommand(monitor);
					}
					catch (Exception e)
					{
						ServoyLog.logError(e);
					}
					if (cleanInstall.get())
					{
						cleanInstall.set(false);
						npmCommand = Activator.getInstance().createNPMCommand(Arrays.asList("ci", "--legacy-peer-deps"));
						try
						{
							npmCommand.runCommand(monitor);
						}
						catch (Exception e)
						{
							ServoyLog.logError(e);
						}
					}
					else
					{
						npmCommand = Activator.getInstance().createNPMCommand(Arrays.asList("update", "--legacy-peer-deps"));
						try
						{
							npmCommand.runCommand(monitor);
						}
						catch (Exception e)
						{
							ServoyLog.logError(e);
						}
					}
					npmCommand = Activator.getInstance().createNPMCommand(Arrays.asList("dedup"));
					try
					{
						npmCommand.runCommand(monitor);
					}
					catch (Exception e)
					{
						ServoyLog.logError(e);
					}
					if (SOURCE_DEBUG)
					{
						writeConsole(console, "SOURCE DEBUG, skipping npm run build_debug_nowatch, need to be run by your self (npm install did happen)");
					}
					else
					{
						npmCommand = Activator.getInstance().createNPMCommand(Arrays.asList("run",
							warExportModel != null ? "sourcemaps".equals(warExportModel.exportNG2Mode()) ? "build_sourcemap" : "build"
								: "build_debug_nowatch"));
						try
						{
							npmCommand.runCommand(monitor);
						}
						catch (Exception e)
						{
							ServoyLog.logError(e);
						}
					}
				}
				writeConsole(console, "Total time to check/install Titanium NG target folder: " + projectFolder + " is " +
					Math.round((System.currentTimeMillis() - time) / 1000) + "s\n");
				return Status.OK_STATUS;
			}
			finally
			{
				try
				{
					console.close();
				}
				catch (IOException e)
				{
				}
				// remove the schedules 1 and start + 1 from the scheduled.
				// if that is still higher then 0 we need to start another.
				if (scheduled.addAndGet(-2) <= 0)
				{
					// this can be a WAR export, then it was 1 not 2 when running.
					scheduled.set(0);
				}
				else
				{
					// first just set it now to 0 to start over.
					scheduled.set(0);
					checkPackages(false);
				}
			}
		}

		/*
		 * map<templateName , Pair<spec, tagName>>
		 */
		private LayoutTemplates generateStructureTemplate(Map<String, Pair<WebLayoutSpecification, String>> tags)
		{
			StringBuilder template = new StringBuilder();
			StringBuilder viewChild = new StringBuilder();
			StringBuilder templateLFC = new StringBuilder();

			template.append("<!-- structure template generate start -->\n");
			templateLFC.append("<!-- structure template generate start -->\n");
			viewChild.append("// structure viewchild template generate start\n");

			tags.forEach((templateName, pair) -> {
				template.append("<ng-template  #svyResponsive");
				template.append(templateName);
				template.append("  let-state=\"state\" >\n<");
				template.append(pair.getRight());
				pair.getLeft().getDirectives().forEach(directive -> template.append(' ').append(directive));
				template.append(" [svyContainerStyle]=\"state\" class=\"svy-layoutcontainer\">\n");
				template.append(
					"<ng-template *ngFor=\"let item of state.items\" [ngTemplateOutlet]=\"getTemplate(item)\" [ngTemplateOutletContext]=\"{ state:item, callback:this}\"></ng-template>\n</");
				template.append(pair.getRight());
				template.append(">\n</ng-template>\n");

				templateLFC.append("<ng-template  #svyResponsive");
				templateLFC.append(templateName);
				templateLFC.append("  let-state=\"state\" let-row=\"row\" let-i=\"i\">\n<");
				templateLFC.append(pair.getRight());
				pair.getLeft().getDirectives().forEach(directive -> templateLFC.append(' ').append(directive));
				templateLFC.append(" [svyContainerStyle]=\"state\" class=\"svy-layoutcontainer\">\n");
				templateLFC.append(
					"<ng-template *ngFor=\"let item of state.items\" [ngTemplateOutlet]=\"getRowItemTemplate(item)\" [ngTemplateOutletContext]=\"{ state:getRowItemState(item, row, i), callback:this, row:row, i:i}\"></ng-template>\n</");
				templateLFC.append(pair.getRight());
				templateLFC.append(">\n</ng-template>\n");

				viewChild.append("@ViewChild('svyResponsive");
				viewChild.append(templateName);
				viewChild.append("', { static: true }) readonly svyResponsive");
				viewChild.append(templateName);
				viewChild.append(": TemplateRef<any>;\n");
			});
			template.append("<!-- structure template generate end -->");
			templateLFC.append("<!-- structure template generate end -->");
			viewChild.append("// structure viewchild template generate end");
			return new LayoutTemplates()
			{
				@Override
				public CharSequence getViewChilds()
				{
					return viewChild;
				}

				public CharSequence getLFFormComponentTemplate()
				{
					return templateLFC;
				}

				@Override
				public CharSequence getFormComponentTemplate()
				{
					return template;
				}
			};
		}

		/**
		 * @param console
		 * @param pck
		 */
		private void writeConsole(StringOutputStream console, String message)
		{
			try
			{
				console.write(message + "\n");
			}
			catch (IOException e2)
			{
			}
		}

		/**
		 * @param packageToInstall
		 * @param dependencies
		 * @param packageName
		 * @param packageReader
		 * @param entryPoint
		 */
		private String checkPackage(JSONObject dependencies, String packageName, IPackageReader packageReader, String entryPoint, StringOutputStream console)
		{
			String packageVersion = packageReader.getVersion();
			if (entryPoint != null)
			{
				if (Arrays.binarySearch(defaultPackages, packageName) >= 0)
				{
					// test if this internal package should be ignored:
					if (isDefaultPackageEnabled(packageName))
					{
						File projectFolder = Activator.getInstance().getProjectFolder();
						File packageFolder = new File(projectFolder, entryPoint);
						File tsConfig = new File(projectFolder, "tsconfig.json");
						try
						{
							String tsConfigContents = FileUtils.readFileToString(tsConfig, "UTF8");
							JSONObject json = new JSONObject(tsConfigContents);
							JSONObject paths = json.getJSONObject("compilerOptions").getJSONObject("paths");
							if (!paths.has(packageName))
							{
								JSONArray array = new JSONArray();
								array.put(new File(packageFolder, "src/public-api").getCanonicalPath());
								paths.put(packageName, array);
								FileUtils.write(tsConfig, json.toString(1), "UTF8", false);
							}
							String installedVersion = dependencies.optString(packageName);
							if (!installedVersion.endsWith(entryPoint))
							{
								return packageFolder.getCanonicalPath();
							}
							return null;
						}
						catch (Exception e)
						{
							ServoyLog.logError(e);
						}
					}
					else return null;
				}
				// its a file based service (something installed in the workspace as a source project)
				else if (packageReader instanceof DirPackageReader)
				{
					try
					{
						IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
						IContainer[] containers = root.findContainersForLocationURI(packageReader.getPackageURL().toURI());
						if (containers != null && containers.length > 0)
						{
							try
							{
								IProject project = null;
								if (containers.length == 1)
								{
									project = containers[0].getProject();
								}
								else
								{
									for (IContainer container : containers)
									{
										if (container instanceof IProject)
										{
											project = (IProject)container;
											break;
										}
									}
									if (project == null) project = containers[0].getProject();
								}
								IFile sourcePath = project.getFile(".sourcepath");
								if (sourcePath.exists())
								{
									File projectFolder = Activator.getInstance().getProjectFolder();
									File packagesFolder = new File(projectFolder, "packages");
									File packageFolder = new File(packagesFolder, packageName);

									JSONObject sourcePathJson = null;
									String sourcePathContents = FileUtils.readFileToString(sourcePath.getLocation().toFile(), "UTF8");
									sourcePathJson = new JSONObject(sourcePathContents);
									IFolder file = project.getFolder(sourcePathJson.getString("srcDir"));
									File apiFile = new File(packageFolder, sourcePathJson.getString("apiFile") + ".ts");

									String location = packageFolder.getCanonicalPath();
									File packageJson = new File(packageFolder, "package.json");
									boolean packageJsonChanged = !packageJson.exists();
									if (!packageJsonChanged)
									{
										// this only works once at startup, after that the DirectorySync already pushed a new value before this check
										packageJsonChanged = file.getFile("package.json").getLocation().toFile().lastModified() -
											packageJson.lastModified() > 1000;
									}
									// check/copy the dist folder to the target packages location
									if (!WebPackagesListener.watchCreated.containsKey(packageReader.getPackageName()) || !packageFolder.exists() ||
										(apiFile != null && !apiFile.exists()))
									{
										DirectorySync directorySync = WebPackagesListener.watchCreated.get(packageReader.getPackageName());
										if (directorySync != null) directorySync.destroy();
										FileUtils.deleteQuietly(packageFolder);
										File srcDir = file.getLocation().toFile();
										FileUtils.copyDirectory(srcDir, packageFolder);
										WebPackagesListener.watchCreated.put(packageReader.getPackageName(), new DirectorySync(srcDir, packageFolder, null));
									}
									// also add if this is a src thing to the ts config
									if (sourcePathJson != null)
									{
										File tsConfig = new File(projectFolder, "tsconfig.json");
										String tsConfigContents = FileUtils.readFileToString(tsConfig, "UTF8");
										JSONObject json = new JSONObject(tsConfigContents);
										JSONObject paths = json.getJSONObject("compilerOptions").getJSONObject("paths");
										if (!paths.has(packageName))
										{
											JSONArray array = new JSONArray();
											array.put(new File(packageFolder, sourcePathJson.getString("apiFile")).getCanonicalPath());
											paths.put(packageName, array);
											FileUtils.write(tsConfig, json.toString(1), "UTF8", false);
										}
									}

									String installedVersion = dependencies.optString(packageName);
									if (packageJsonChanged || !installedVersion.endsWith("packages/" + packageName))
									{
										return location;
									}
								}
								else
								{
									writeConsole(console, "Source package " + packageName + " (project: " + project +
										") should have a .sourcepath json file in the project root having 2 properties: srcDir (pointing to the root souce dir) and apiFile (pointing to the public api file without extension in that source dir\n");
								}
								return null;
							}
							catch (IOException e)
							{
								ServoyLog.logError(e);
							}
						}
					}
					catch (URISyntaxException e)
					{
						ServoyLog.logError(e);
					}
				}
				else if (packageReader instanceof ZipPackageReader)
				{

					// this has an entry point in the zip, extract this package.
					File projectFolder = Activator.getInstance().getProjectFolder();
					File packagesFolder = new File(projectFolder, "packages");
					File packageFolder = new File(packagesFolder, packageName);

					// if we previously had a project reference and now we switched back to the zip we need to remove what was generated for it
					try
					{
						if (WebPackagesListener.watchCreated.containsKey(packageReader.getPackageName()))
						{
							DirectorySync directorySync = WebPackagesListener.watchCreated.remove(packageReader.getPackageName());
							if (directorySync != null) directorySync.destroy();
							if (packageFolder.exists()) FileUtils.deleteQuietly(packageFolder);

							File tsConfig = new File(projectFolder, "tsconfig.json");
							String tsConfigContents = FileUtils.readFileToString(tsConfig, "UTF8");
							JSONObject json = new JSONObject(tsConfigContents);
							JSONObject paths = json.getJSONObject("compilerOptions").getJSONObject("paths");
							if (paths.has(packageName))
							{
								if (paths.remove(packageName) != null)
								{
									FileUtils.write(tsConfig, json.toString(1), "UTF8", false);
								}
							}
						}
					}
					catch (IOException e)
					{
						ServoyLog.logError(e);
					}

					boolean exists = packageFolder.exists();
					if (exists)
					{
						File entry = new File(packageFolder, ".timestamp");
						try
						{
							if (!entry.exists() || Long.parseLong(FileUtils.readFileToString(entry, "UTF8")) != packageReader.getResource().lastModified())
							{
								FileUtils.deleteQuietly(packageFolder);
								exists = false;
							}
						}
						catch (Exception e)
						{
							FileUtils.deleteQuietly(packageFolder);
							exists = false;
						}

					}

					try
					{
						if (!exists)
						{
							ZipUtils.extractZip(packageReader.getResource().toURI().toURL(), packageFolder);
							FileUtils.writeStringToFile(new File(packageFolder, ".timestamp"), Long.toString(packageReader.getResource().lastModified()),
								"UTF8");
						}
						File entry = new File(packageFolder, entryPoint);
						if (entry.exists())
						{
							String installedVersion = dependencies.optString(packageName);
							if (!exists || !installedVersion.endsWith(entryPoint))
							{
								return entry.getCanonicalPath();
							}
							return null;
						}
					}
					catch (IOException e)
					{
						ServoyLog.logError(e);
					}
				}

				// check for prerelease (npm uses x.y.z-rc1 manifest: x.y.z.rc1)
				String[] split = packageVersion.split("\\.");
				if (split.length == 4)
				{
					packageVersion = split[0] + '.' + split[1] + '.' + split[2] + '-' + split[3];
				}
				String installedVersion = dependencies.optString(packageName);
				if (!installedVersion.contains(packageVersion))
				{
					return packageName + '@' + packageVersion;
				}
			}

			return null;
		}

		private String replace(String content, String start, String end, CharSequence toInsert)
		{
			int startIndex = content.indexOf(start);
			int endIndex = content.indexOf(end) + end.length();
			return content.substring(0, startIndex) + toInsert + content.substring(endIndex);
		}
	}

	private static boolean SOURCE_DEBUG = false;

	private static final AtomicBoolean ignore = new AtomicBoolean(false);

	private static final AtomicInteger scheduled = new AtomicInteger(0); // 0 == no jobs, 1 == job scheduled, 2  or 3 == job running, 3 == run again.
	private static final AtomicBoolean cleanInstall = new AtomicBoolean(false);

	private static final ConcurrentMap<String, DirectorySync> watchCreated = new ConcurrentHashMap<>();

	public WebPackagesListener()
	{
		if (WebServiceSpecProvider.isLoaded())
			checkPackages(false);
	}

	@Override
	public void ngPackagesChanged(CHANGE_REASON changeReason, boolean loadedPackagesAreTheSameAlthoughReferencingModulesChanged)
	{
		checkPackages(false);
	}

	public static boolean isBuildRunning()
	{
		return scheduled.get() > 1;
	}

	/**
	 * returns true if the given package is enabled, will return falso only when the given package is a ng1 default package mapping
	 * and the preference says it is disabled
	 * @param packageName
	 * @return
	 */
	private static boolean isDefaultPackageEnabled(String packageName)
	{
		String ng1Name = NG1MAPPING.get(packageName);
		return ng1Name != null ? ServoyModelFinder.getServoyModel().getNGPackageManager().isDefaultPackageEnabled(ng1Name) : true;
	}

	/**
	 * if true then checkpackage will be blocked until it is called with false again
	 * if false then all call to checkpackage will run a job, this will also trigger a checkPackage itself to make sure that all the blocked calls will now be triggerd once
	 * @param ignore
	 */
	public static void setIgnore(boolean ignore)
	{
		WebPackagesListener.ignore.set(ignore);
		if (!ignore) checkPackages(false);
	}

	public static void checkPackages(boolean ci)
	{
		if (ServoyModelFinder.getServoyModel().getActiveProject() == null || ignore.get())
		{
			return;
		}
		if (ci) cleanInstall.set(ci);
		// only schedule 1 and a bit later to relax first the system
		if (scheduled.compareAndSet(0, 1))
		{
			Job job = new PackageCheckerJob("Checking/Installing NGClient2 Components and Services", null);
			job.schedule(5000);
		}
		else if (scheduled.get() == 2)
		{
			// there is only 1 job and that one is running (not scheduled) then increase by one to make it run again
			scheduled.incrementAndGet();
		}
	}

	/**
	 *  exports the state location dir to the location given that should be a WAR layout
	 *  The index.html page will be put in WEB-INF/angular-index.html the rest in the root.
	 * @throws IOException
	 */
	public static void exportNG2ToWar(ING2WarExportModel model)
	{
		Activator activator = Activator.getInstance();
		activator.waitForNodeExtraction();

		try
		{
			File distFolder = new File(activator.getProjectFolder(), "dist/app_prod");
			FileUtils.deleteQuietly(distFolder);
			// create the production build
			new PackageCheckerJob("production_build", model.getModel()).run(model.getProgressMonitor());
			// copy the production build
			if (distFolder.exists())
			{
				FileUtils.copyDirectory(distFolder, model.getExportLocation(), (path) -> !path.getName().equals("index.html"));

				FileUtils.copyFile(new File(distFolder, "index.html"), new File(model.getExportLocation(), "WEB-INF/angular-index.html"));
			}
			else
			{
				throw new RuntimeException("NGClient2 production resources not generated, see log");
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error generating NGClient2 production resources", e);
		}
	}

	private interface LayoutTemplates
	{
		CharSequence getFormComponentTemplate();

		CharSequence getLFFormComponentTemplate();

		CharSequence getViewChilds();
	}
}
