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

package com.servoy.eclipse.ui.views.solutionexplorer.actions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Manifest;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.widgets.Shell;
import org.sablo.specification.Package.IPackageReader;
import org.sablo.specification.WebObjectSpecification;

import com.servoy.eclipse.core.ServoyModel;
import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.eclipse.core.util.RunInWorkspaceJob;
import com.servoy.eclipse.core.util.UIUtils.MessageAndCheckBoxDialog;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.ui.node.SimpleUserNode;
import com.servoy.eclipse.ui.node.UserNodeType;
import com.servoy.eclipse.ui.views.solutionexplorer.SolutionExplorerTreeContentProvider;
import com.servoy.eclipse.ui.views.solutionexplorer.SolutionExplorerView;

/**
 * Deletes the selected components or services or packages of components or services.
 *
 * @author gganea
 */
public class DeleteComponentOrServiceOrPackageResourceAction extends Action implements ISelectionChangedListener
{

	private IStructuredSelection selection;
	private final Shell shell;
	private final UserNodeType nodeType;
	private boolean deleteFromDisk;
	private final SolutionExplorerView viewer;


	public DeleteComponentOrServiceOrPackageResourceAction(Shell shell, String text, UserNodeType nodeType, SolutionExplorerView viewer)
	{
		this.shell = shell;
		this.nodeType = nodeType;
		this.viewer = viewer;
		setText(text);
		setToolTipText(text);
	}

	@Override
	public void run()
	{
		if (selection != null)
		{
			//first save the current selection so that he user can't change it while the job is running
			List<SimpleUserNode> savedSelection = new ArrayList<SimpleUserNode>();
			Iterator<SimpleUserNode> it = selection.iterator();
			boolean packageProjectSelected = false;
			while (it.hasNext())
			{
				SimpleUserNode next = it.next();
				savedSelection.add(next);
				if (next.getRealObject() instanceof IPackageReader &&
					SolutionExplorerTreeContentProvider.getResource((IPackageReader)next.getRealObject()) instanceof IProject) packageProjectSelected = true;
			}

			if (packageProjectSelected)
			{
				MessageAndCheckBoxDialog dialog = new MessageAndCheckBoxDialog(shell, "Delete Package", null, "Are you sure you want to delete?",
					"Delete package contents on disk (cannot be undone)                                                   ", false, MessageDialog.QUESTION,
					new String[] { "Ok", "Cancel" }, 0);
				if (dialog.open() == 0)
				{
					deleteFromDisk = dialog.isChecked();
					startDeleteJob(savedSelection);
				}
			}
			else if (MessageDialog.openConfirm(shell, getText(), "Are you sure you want to delete?"))
			{
				startDeleteJob(savedSelection);
			}
		}
	}

	private void startDeleteJob(List<SimpleUserNode> saveTheSelection)
	{
		//start the delete job
		RunInWorkspaceJob deleteJob = new RunInWorkspaceJob(new DeleteComponentOrServiceResourcesWorkspaceJob(saveTheSelection));
		deleteJob.setName("Deleting component or service resources");
		deleteJob.setRule(ServoyModel.getWorkspace().getRoot());
		deleteJob.setUser(false);
		deleteJob.schedule();
	}

	private class DeleteComponentOrServiceResourcesWorkspaceJob implements IWorkspaceRunnable
	{

		private final List<SimpleUserNode> savedSelection;

		public DeleteComponentOrServiceResourcesWorkspaceJob(List<SimpleUserNode> selection)
		{
			savedSelection = selection;
		}

		@Override
		public void run(IProgressMonitor monitor) throws CoreException
		{
			ServoyProject activeProject = ServoyModelManager.getServoyModelManager().getServoyModel().getActiveProject();
			IProject resources = activeProject == null || activeProject.getResourcesProject() == null ? null : activeProject.getResourcesProject().getProject();
			for (SimpleUserNode selected : savedSelection)
			{
				Object realObject = selected.getRealObject();
				IResource resource = null;
				if (realObject instanceof IResource)
				{
					resource = (IResource)realObject;
				}
				else if (realObject instanceof IPackageReader)
				{
					resource = SolutionExplorerTreeContentProvider.getResource((IPackageReader)realObject);
				}
				else if (resources != null &&
					(selected.getType() == UserNodeType.COMPONENT || selected.getType() == UserNodeType.SERVICE || selected.getType() == UserNodeType.LAYOUT))
				{
					resource = getComponentFolderToDelete(resources, selected);
				}

				if (resource != null)
				{
					try
					{
						if (resource instanceof IFolder)
						{
							if (resources != null) resources.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
							deleteFolder((IFolder)resource);
						}
						else
						{
							if (resource instanceof IProject)
							{
								IProject[] referencingProjects = ((IProject)resource).getReferencingProjects();
								for (IProject iProject : referencingProjects)
								{
									RemovePackageProjectReferenceAction.removeProjectReference(iProject, (IProject)resource);
								}
								((IProject)resource).delete(deleteFromDisk, true, monitor);
							}
							else
							{
								resource.delete(true, new NullProgressMonitor());
							}
							if (resources != null) resources.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
						}
						viewer.refreshTreeCompletely();
					}
					catch (CoreException e)
					{
						ServoyLog.logError(e);
					}
				}
			}
		}

		private void deleteFolder(IContainer folder) throws CoreException
		{
			for (IResource resource : folder.members())
			{
				if (resource instanceof IFolder)
				{
					deleteFolder((IFolder)resource);
				}
				else
				{
					try
					{
						resource.delete(true, new NullProgressMonitor());
					}
					catch (CoreException e)
					{
						// can go wrong once, try again with a refresh
						resource.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
						resource.delete(true, new NullProgressMonitor());
					}
				}
			}
			folder.delete(true, new NullProgressMonitor());
		}

		private IResource getComponentFolderToDelete(IProject resources, SimpleUserNode next)
		{
			WebObjectSpecification spec = (WebObjectSpecification)next.getRealObject();
			IContainer[] dirResource;
			IResource resource = null;
			OutputStream out = null;
			InputStream in = null;
			InputStream contents = null;
			try
			{
				dirResource = resources.getWorkspace().getRoot().findContainersForLocationURI(spec.getSpecURL().toURI());
				if (dirResource.length == 1 && dirResource[0].getParent().exists()) resource = dirResource[0].getParent();

				if (resource != null)
				{
					IResource parent = resource.getParent();

					IFile m = getFile(parent);
					m.refreshLocal(IResource.DEPTH_ONE, new NullProgressMonitor());
					contents = m.getContents();
					Manifest manifest = new Manifest(contents);
					manifest.getEntries().remove(resource.getName() + "/" + resource.getName() + ".spec");
					out = new ByteArrayOutputStream();
					manifest.write(out);
					in = new ByteArrayInputStream(out.toString().getBytes());
					m.setContents(in, IResource.FORCE, new NullProgressMonitor());
				}
			}
			catch (URISyntaxException e)
			{
				ServoyLog.logError(e);
			}
			catch (IOException e)
			{
				ServoyLog.logError(e);
			}
			catch (CoreException e)
			{
				ServoyLog.logError(e);
			}
			finally
			{
				try
				{
					if (out != null) out.close();
					if (in != null) in.close();
					if (contents != null) contents.close();
				}
				catch (IOException e)
				{
					ServoyLog.logError(e);
				}
			}
			return resource;
		}

		private IFile getFile(IResource resource)
		{
			String manifest = "META-INF/MANIFEST.MF";
			if (resource instanceof IFolder) return ((IFolder)resource).getFile(manifest);
			if (resource instanceof IProject) return ((IProject)resource).getFile(manifest);
			return null;

		}

	}

	@Override
	public void selectionChanged(SelectionChangedEvent event)
	{
		// allow multiple selection
		selection = null;
		IStructuredSelection sel = (IStructuredSelection)event.getSelection();
		boolean state = true;
		Iterator<SimpleUserNode> it = sel.iterator();
		while (it.hasNext() && state)
		{
			SimpleUserNode node = it.next();
			state = (node.getRealType() == nodeType);
			if (node.getType() == UserNodeType.COMPONENT || node.getType() == UserNodeType.SERVICE || node.getType() == UserNodeType.LAYOUT)
			{
				if (node.getRealObject() instanceof WebObjectSpecification)
				{
					state = !((WebObjectSpecification)node.getRealObject()).getSpecURL().getProtocol().equals("jar");
				}
				else state = node.parent.getRealObject() instanceof IFolder || node.parent.getRealObject() instanceof IProject;
			}
			else if (node.getRealObject() instanceof IFolder)
			{
				state = state && !"META-INF".equals(((IFolder)node.getRealObject()).getName());
			}
		}
		if (state)
		{
			selection = sel;
		}
		setEnabled(state);
	}

}
