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
package com.servoy.eclipse.ui.wizards;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.IntStream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.dltk.javascript.core.JavaScriptNature;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

import com.servoy.eclipse.core.ServoyModel;
import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.eclipse.core.quickfix.ChangeResourcesProjectQuickFix.IValidator;
import com.servoy.eclipse.core.quickfix.ChangeResourcesProjectQuickFix.ResourcesProjectChooserComposite;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.eclipse.model.nature.ServoyResourcesProject;
import com.servoy.eclipse.model.repository.EclipseRepository;
import com.servoy.eclipse.model.repository.RepositorySettingsDeserializer;
import com.servoy.eclipse.model.repository.SolutionSerializer;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.model.util.WorkspaceFileAccess;
import com.servoy.eclipse.ui.Activator;
import com.servoy.eclipse.ui.util.DocumentValidatorVerifyListener;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.Media;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.ScriptNameValidator;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.persistence.SolutionMetaData;
import com.servoy.j2db.server.ngclient.startup.resourceprovider.ResourceProvider;
import com.servoy.j2db.util.Utils;
import com.servoy.j2db.util.docvalidator.IdentDocumentValidator;

/**
 * Wizard used in order to create a new Servoy solution project. Will optionally create linked resource project (with styles & other info).
 *
 * @author acostescu
 */
public class NewSolutionWizard extends Wizard implements INewWizard
{
	public static final String ID = "com.servoy.eclipse.ui.NewSolutionWizard";

	protected NewSolutionWizardPage page1;

	/**
	 * Creates a new wizard.
	 */
	public NewSolutionWizard()
	{
		setWindowTitle("New solution");
		setDefaultPageImageDescriptor(Activator.loadImageDescriptorFromBundle("solution_wizard_description.png"));
	}

	public void init(IWorkbench workbench, IStructuredSelection selection)
	{
		page1 = new NewSolutionWizardPage("New Solution");
	}

	@Override
	public void addPages()
	{
		addPage(page1);
	}

	@Override
	public boolean performFinish()
	{
		final ServoyModel servoyModel = ServoyModelManager.getServoyModelManager().getServoyModel();

		IRunnableWithProgress newSolutionRunnable = new IRunnableWithProgress()
		{

			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException
			{
				monitor.beginTask("Creating solution and writing files to disk", 4);
				// create Solution object
				EclipseRepository repository = (EclipseRepository)ServoyModel.getDeveloperRepository();
				try
				{
					Solution solution = (Solution)repository.createNewRootObject(page1.getNewSolutionName(), IRepository.SOLUTIONS);

					monitor.setTaskName("Setting up resource project and reference");
					IProject resourceProject;
					// create Resource project if needed
					if (page1.getResourceProjectData().getNewResourceProjectName() != null)
					{
						// create a new resource project
						String resourceProjectName = page1.getResourceProjectData().getNewResourceProjectName();
						resourceProject = ServoyModel.getWorkspace().getRoot().getProject(resourceProjectName);
						resourceProject.create(null);
						resourceProject.open(null);

						// write repositoy UUID into the resource project
						RepositorySettingsDeserializer.writeRepositoryUUID(new WorkspaceFileAccess(ResourcesPlugin.getWorkspace()), resourceProjectName,
							repository.getRepositoryUUID());

						IProjectDescription resourceProjectDescription = resourceProject.getDescription();
						resourceProjectDescription.setNatureIds(new String[] { ServoyResourcesProject.NATURE_ID });
						resourceProject.setDescription(resourceProjectDescription, null);
					}
					else
					{
						resourceProject = page1.getResourceProjectData().getExistingResourceProject() != null
							? page1.getResourceProjectData().getExistingResourceProject().getProject() : null;
					}

					monitor.setTaskName("Creating and opening project");
					// as the serialization is done using java.io, we must make sure the Eclipse resource structure
					// stays up to date; we create a project, then we must open it and add servoy solution nature
					IProject newProject = ServoyModel.getWorkspace().getRoot().getProject(page1.getNewSolutionName());
					String location = page1.getProjectLocation();
					IProjectDescription description = ServoyModel.getWorkspace().newProjectDescription(page1.getNewSolutionName());
					if (location != null)
					{
						IPath path = new Path(location);
						path = path.append(page1.getNewSolutionName());
						description.setLocation(path);
					}
					newProject.create(description, null);
					newProject.open(null);
					newProject.getFile(SolutionSerializer.GLOBALS_FILE).create(Utils.getUTF8EncodedStream(""), true, null);
					monitor.worked(1);

					if (solution != null)
					{
						int solutionType = page1.getSolutionType();
						solution.setSolutionType(solutionType);

						// serialize Solution object to given project
						repository.updateRootObject(solution);

						addDefaultThemeIfNeeded(repository, solution);
					}
					monitor.worked(1);

					monitor.setTaskName("Registering natures");
					description.setNatureIds(new String[] { ServoyProject.NATURE_ID, JavaScriptNature.NATURE_ID });
					monitor.worked(1);

					// link solution project to the resource project; store project description

					if (resourceProject != null)
					{
						description.setReferencedProjects(new IProject[] { resourceProject });
					}
					newProject.setDescription(description, null);
					monitor.worked(1);
					monitor.done();
				}
				catch (final Exception e)
				{
					monitor.done();
					ServoyLog.logError(e);
					Display.getDefault().syncExec(new Runnable()
					{
						public void run()
						{
							MessageDialog.openError(Display.getDefault().getActiveShell(), "Cannot create new solution", e.getMessage());
						}
					});
				}
			}

			private void addDefaultThemeIfNeeded(EclipseRepository repository, Solution solution) throws RepositoryException, IOException
			{
				if (page1.shouldAddDefaultTheme())
				{
					Media defaultTheme = addMediaFile(solution, "resources/default-theme.less", solution.getName() + ".less");
					addMediaFile(solution, "resources/" + ResourceProvider.PROPERTIES_LESS, ResourceProvider.PROPERTIES_LESS);
					solution.setStyleSheetID(defaultTheme.getID());
					repository.updateRootObject(solution);
				}
			}

			private Media addMediaFile(Solution solution, String path, String fileName) throws RepositoryException, IOException
			{
				Media defaultTheme = solution.createNewMedia(new ScriptNameValidator(), fileName);
				defaultTheme.setMimeType("text/css");
				try (InputStream is = NewSolutionWizard.class.getResource(path).openStream())
				{
					defaultTheme.setPermMediaData(Utils.getBytesFromInputStream(is));
				}
				return defaultTheme;
			}
		};

		final ServoyProject activeProject = servoyModel.getActiveProject();
		final Solution activeEditingSolution = (activeProject != null) ? activeProject.getEditingSolution() : null;
		final String jobName;
		final boolean addAsModuleToActiveSolution = shouldAddAsModule(activeEditingSolution);
		if (addAsModuleToActiveSolution)
		{
			jobName = "Adding as module to active solution";
		}
		else
		{
			jobName = "Activating new solution";
		}

		IRunnableWithProgress solutionActivationRunnable = new IRunnableWithProgress()
		{

			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException
			{
				monitor.beginTask(jobName, 1);
				servoyModel.refreshServoyProjects();
				// set this solution as the new active solution or add it as a module
				ServoyProject newProject = servoyModel.getServoyProject(page1.getNewSolutionName());
				if (newProject != null)
				{
					if (addAsModuleToActiveSolution)
					{
						boolean doesNotHaveIt = true; // sometimes it might happen that the solution already referenced an unavailable module that was now created (so before there was an error marker); we don't want a double reference

						String modules = activeEditingSolution.getModulesNames();

						if (modules == null) modules = "";
						String[] moduleNames = Utils.getTokenElements(modules, ",", true);
						if (modules.trim().length() > 0) modules = modules + ",";
						for (String moduleName : moduleNames)
						{
							if (newProject.getProject().getName().equals(moduleName))
							{
								doesNotHaveIt = false;
								break;
							}
						}

						if (doesNotHaveIt)
						{
							activeEditingSolution.setModulesNames(modules + newProject.getProject().getName());
							try
							{
								activeProject.saveEditingSolutionNodes(new IPersist[] { activeEditingSolution }, false);
							}
							catch (RepositoryException e)
							{
								ServoyLog.logError(e);
							}
						}
					}
					else
					{
						servoyModel.setActiveProject(newProject, true);
					}
				}
				else
				{
					ServoyLog.logError("cannot activate solution", null);
				}
				monitor.worked(1);
				monitor.done();
			}
		};

		try
		{
			PlatformUI.getWorkbench().getProgressService().run(true, false, newSolutionRunnable);
			PlatformUI.getWorkbench().getProgressService().run(true, false, solutionActivationRunnable);
		}
		catch (Exception e)
		{
			ServoyLog.logError(e);
		}

		return true;
	}

	/**
	 * @param activeEditingSolution
	 * @return
	 */
	protected boolean shouldAddAsModule(final Solution activeEditingSolution)
	{
		return (page1.getSolutionType() == SolutionMetaData.MODULE || page1.getSolutionType() == SolutionMetaData.NG_MODULE ||
			page1.getSolutionType() == SolutionMetaData.PRE_IMPORT_HOOK || page1.getSolutionType() == SolutionMetaData.POST_IMPORT_HOOK) &&
			activeEditingSolution != null;
	}

	public static class NewSolutionWizardPage extends WizardPage implements Listener, IValidator
	{
		private String solutionName;
		private int solutionType = SolutionMetaData.SOLUTION;

		private Text solutionNameField;
		private Combo solutionTypeCombo;
		private int[] solutionTypeComboValues;
		private ResourcesProjectChooserComposite resourceProjectComposite;
		private ProjectLocationComposite projectLocationComposite;
		private Button defaultThemeCheck;
		private Boolean addDefaultTheme = null;


		/**
		 * Creates a new solution creation wizard page.
		 *
		 * @param pageName the name of the page
		 */
		public NewSolutionWizardPage(String pageName)
		{
			super(pageName);
			setTitle("Create a new solution");
			setDescription("- a new Servoy solution project will be created");
		}

		public boolean shouldAddDefaultTheme()
		{
			return addDefaultTheme != null ? (addDefaultTheme.booleanValue() && SolutionMetaData.isNGOnlySolution(solutionType))
				: solutionType == SolutionMetaData.NG_CLIENT_ONLY;
		}

		public String getProjectLocation()
		{
			return projectLocationComposite.getProjectLocation();
		}

		/**
		 * Returns the composite that handles the resources project data. It can be used to determine what the user selected.
		 *
		 * @return the composite that handles the resources project data. It can be used to determine what the user selected.
		 */
		public ResourcesProjectChooserComposite getResourceProjectData()
		{
			return resourceProjectComposite;
		}

		/**
		 * Returns the name of the new solution.
		 *
		 * @return the name of the new solution.
		 */
		public String getNewSolutionName()
		{
			return solutionName;
		}

		/**
		 * Returns the selected solution type. One of {@link SolutionMetaData#MODULE} or {@link SolutionMetaData#SOLUTION} constants.
		 *
		 * @return the selected solution type. One of {@link SolutionMetaData#MODULE} or {@link SolutionMetaData#SOLUTION} constants.
		 */
		public int getSolutionType()
		{
			return solutionType;
		}

		public void createControl(Composite parent)
		{
			initializeDialogUnits(parent);

			// top level group
			Composite topLevel = new Composite(parent, SWT.NONE);
			topLevel.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));
			setControl(topLevel);

			// solution name UI
			Label solutionLabel = new Label(topLevel, SWT.NONE);
			solutionLabel.setText("Solution name");

			solutionNameField = new Text(topLevel, SWT.BORDER);
			solutionNameField.addVerifyListener(DocumentValidatorVerifyListener.IDENT_SERVOY_VERIFIER);
			solutionNameField.addModifyListener(new ModifyListener()
			{
				public void modifyText(ModifyEvent e)
				{
					handleSolutionNameChanged();
				}
			});

			defaultThemeCheck = new Button(topLevel, SWT.CHECK);
			defaultThemeCheck.setText("Add default servoy .less theme (configurable by a less properties file)");
			defaultThemeCheck.addSelectionListener(new SelectionAdapter()
			{
				@Override
				public void widgetSelected(SelectionEvent e)
				{
					addDefaultTheme = Boolean.valueOf(defaultThemeCheck.getSelection());
				}
			});
			defaultThemeCheck.setSelection(true);//ng solution is selected by default

			// solution type
			Label solutionTypeLabel = new Label(topLevel, SWT.NONE);
			solutionTypeLabel.setText("Solution type");
			solutionTypeCombo = new Combo(topLevel, SWT.DROP_DOWN | SWT.READ_ONLY);

			String[] solutionTypeNames = new String[SolutionMetaData.solutionTypeNames.length - 1];
			System.arraycopy(SolutionMetaData.solutionTypeNames, 1, solutionTypeNames, 0, solutionTypeNames.length);
			solutionTypeCombo.setItems(solutionTypeNames);
			solutionTypeComboValues = new int[SolutionMetaData.solutionTypes.length - 1];
			System.arraycopy(SolutionMetaData.solutionTypes, 1, solutionTypeComboValues, 0, solutionTypeComboValues.length);
			solutionTypeCombo.select(IntStream.range(0, solutionTypeComboValues.length).filter(
				i -> SolutionMetaData.NG_CLIENT_ONLY == solutionTypeComboValues[i]).findFirst().getAsInt());
			handleSolutionTypeComboSelected();
			solutionTypeCombo.addSelectionListener(new SelectionAdapter()
			{
				@Override
				public void widgetSelected(SelectionEvent event)
				{
					handleSolutionTypeComboSelected();
				}
			});

			projectLocationComposite = new ProjectLocationComposite(topLevel, SWT.NONE, this.getClass().getName());

			resourceProjectComposite = new ResourcesProjectChooserComposite(topLevel, SWT.NONE, this,
				"Please choose the resources project this solution will reference (for styles, column/sequence info, security)",
				ServoyModelManager.getServoyModelManager().getServoyModel().getActiveResourcesProject(), false);

			// layout of the page
			FormLayout formLayout = new FormLayout();
			formLayout.spacing = 5;
			formLayout.marginWidth = formLayout.marginHeight = 20;
			topLevel.setLayout(formLayout);

			FormData formData = new FormData();
			formData.left = new FormAttachment(0, 0);
			formData.top = new FormAttachment(solutionNameField, 0, SWT.CENTER);
			solutionLabel.setLayoutData(formData);

			formData = new FormData();
			formData.left = new FormAttachment(solutionLabel, 0);
			formData.top = new FormAttachment(0, 0);
			formData.right = new FormAttachment(100, 0);
			solutionNameField.setLayoutData(formData);

			formData = new FormData();
			formData.left = new FormAttachment(0, 0);
			formData.top = new FormAttachment(solutionTypeCombo, 0, SWT.CENTER);
			solutionTypeLabel.setLayoutData(formData);

			formData = new FormData();
			formData.left = new FormAttachment(solutionNameField, 0, SWT.LEFT);
			formData.top = new FormAttachment(solutionNameField, 0, SWT.BOTTOM);
			formData.right = new FormAttachment(100, 0);
			solutionTypeCombo.setLayoutData(formData);

			formData = new FormData();
			formData.left = new FormAttachment(0, 0);
			formData.right = new FormAttachment(100, 0);
			formData.top = new FormAttachment(solutionTypeCombo, 20);
			projectLocationComposite.setLayoutData(formData);

			formData = new FormData();
			formData.left = new FormAttachment(0, 0);
			formData.right = new FormAttachment(100, 0);
			formData.top = new FormAttachment(projectLocationComposite, 30);
			resourceProjectComposite.setLayoutData(formData);

			formData = new FormData();
			formData.left = new FormAttachment(0, 0);
			formData.top = new FormAttachment(resourceProjectComposite, 20);
			formData.bottom = new FormAttachment(100, 0);
			defaultThemeCheck.setLayoutData(formData);

			topLevel.pack();
		}

		@Override
		public void setVisible(boolean visible)
		{
			super.setVisible(visible);
			if (visible)
			{
				solutionNameField.setFocus();
				setPageComplete(validatePage());
			}
		}

		private void handleSolutionTypeComboSelected()
		{
			solutionType = solutionTypeComboValues[solutionTypeCombo.getSelectionIndex()];
			defaultThemeCheck.setEnabled(SolutionMetaData.isNGOnlySolution(solutionType));
			defaultThemeCheck.setSelection(shouldAddDefaultTheme());
		}

		private void handleSolutionNameChanged()
		{
			solutionName = solutionNameField.getText();
			setPageComplete(validatePage());
		}

		public void setSolutionTypes(int[] solTypes, int selected, boolean fixedType)
		{
			String[] solutionTypeNames = new String[solTypes.length];

			for (int j = 0; j < solTypes.length; j++)
			{
				for (int i = 0; i < SolutionMetaData.solutionTypes.length; i++)
				{
					if (SolutionMetaData.solutionTypes[i] == solTypes[j])
					{
						solutionTypeNames[j] = SolutionMetaData.solutionTypeNames[i];
						break;
					}
				}
			}

			solutionTypeComboValues = solTypes;
			solutionTypeCombo.setItems(solutionTypeNames);
			solutionTypeCombo.select(selected);

			handleSolutionTypeComboSelected();
			if (fixedType)
			{
				solutionTypeCombo.setEnabled(false);
			}
		}

		/**
		 * Sees if the data is filled up correctly.
		 *
		 * @return true if the content is OK (page ready); false otherwise.
		 */
		protected boolean validatePage()
		{
			String error = null;
			if (solutionNameField.getText().trim().length() == 0)
			{
				error = "Please give a name for the new solution";
			}
			else
			{
				error = resourceProjectComposite.validate();
				if (error == null && resourceProjectComposite.getNewResourceProjectName() != null &&
					(resourceProjectComposite.getNewResourceProjectName().equalsIgnoreCase(solutionName)))
				{
					error = "You cannot give the same name to the solution and the resource project to be created";
				}
			}
			if (error == null)
			{
				// see if solution name is OK
				if (!IdentDocumentValidator.isJavaIdentifier(solutionName))
				{
					error = "Solution name has unsupported characters";
				}
				else if (solutionName.length() > IRepository.MAX_ROOT_OBJECT_NAME_LENGTH)
				{
					error = "Solution name is too long";
				}
				else if (ServoyModel.getWorkspace().getRoot().getProject(solutionName).exists())
				{
					error = "A project with this name already exists in the workspace";
				}
				else
				{
					IStatus validationResult = ServoyModel.getWorkspace().validateName(solutionName, IResource.PROJECT);
					if (!validationResult.isOK())
					{
						error = "The name of the solution project to be created is not valid: " + validationResult.getMessage();
					}
				}
			}
			setErrorMessage(error);
			return error == null;
		}

		/**
		 * The <code>NewSolutionWizardPage</code> implementation of this <code>Listener</code> method handles all events and enablements for controls on this
		 * page. Subclasses may extend.
		 */
		public void handleEvent(Event event)
		{
			setPageComplete(validatePage());
		}

		public String validate()
		{
			setPageComplete(validatePage());
			return getErrorMessage();
		}

		@Override
		public void performHelp()
		{
			boolean focusNameField = solutionNameField.isFocusControl();
			PlatformUI.getWorkbench().getHelpSystem().displayHelp("com.servoy.eclipse.ui.create_solution");
			if (focusNameField)
			{
				solutionNameField.setFocus();
			}
		}

	}

}