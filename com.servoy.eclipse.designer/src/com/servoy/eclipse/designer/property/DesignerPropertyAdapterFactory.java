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
package com.servoy.eclipse.designer.property;

import java.awt.Dimension;
import java.awt.Point;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.core.runtime.Path;
import org.eclipse.gef.EditPart;
import org.eclipse.ui.views.properties.IPropertySource;

import com.servoy.base.persistence.IMobileProperties;
import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.eclipse.designer.editor.BaseVisualFormEditor;
import com.servoy.eclipse.designer.editor.mobile.editparts.MobileListModel;
import com.servoy.eclipse.designer.mobile.property.MobileComponentWithTitlePropertySource;
import com.servoy.eclipse.designer.mobile.property.MobileListPropertySource;
import com.servoy.eclipse.designer.mobile.property.MobilePersistPropertySource;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.eclipse.model.repository.SolutionSerializer;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.ui.actions.Openable;
import com.servoy.eclipse.ui.editors.PersistEditor;
import com.servoy.eclipse.ui.node.SimpleUserNode;
import com.servoy.eclipse.ui.property.ComplexProperty;
import com.servoy.eclipse.ui.property.DimensionPropertySource;
import com.servoy.eclipse.ui.property.PersistContext;
import com.servoy.eclipse.ui.property.PersistPropertySource;
import com.servoy.eclipse.ui.property.PointPropertySource;
import com.servoy.eclipse.ui.property.RetargetToEditorPersistProperties;
import com.servoy.eclipse.ui.property.SavingPersistPropertySource;
import com.servoy.j2db.persistence.AbstractRepository;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.FormElementGroup;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.IScriptElement;
import com.servoy.j2db.persistence.Media;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.persistence.SolutionMetaData;
import com.servoy.j2db.persistence.StringResource;
import com.servoy.j2db.persistence.Style;
import com.servoy.j2db.util.Pair;

/**
 * Factory for adapters for the Properties view in the designer.
 */
public class DesignerPropertyAdapterFactory implements IAdapterFactory
{
	private static Class[] ADAPTERS = new Class[] { IPropertySource.class, IPersist.class, IResource.class, FormElementGroup.class, Openable.class };

	public Class[] getAdapterList()
	{
		return ADAPTERS;
	}

	public Object getAdapter(Object obj, Class key)
	{
		IPersist persist = null;
		IPersist context = null;
		boolean autoSave = false;
		boolean retargetToEditor = true;

		if (obj instanceof Dimension && key == IPropertySource.class)
		{
			return new DimensionPropertySource(new ComplexProperty<Dimension>((Dimension)obj), null);
		}
		if (obj instanceof Point && key == IPropertySource.class)
		{
			return new PointPropertySource(new ComplexProperty<Point>((Point)obj));
		}

		if (obj instanceof EditPart && ((EditPart)obj).getModel() instanceof FormElementGroup)
		{
			if (key == IPropertySource.class)
			{
				return createFormElementGroupPropertySource((FormElementGroup)((EditPart)obj).getModel(), getEditpartFormContext((EditPart)obj));
			}
			if (key == FormElementGroup.class)
			{
				return ((EditPart)obj).getModel();
			}
			return null;
		}
		if (obj instanceof FormElementGroup && key == IPropertySource.class)
		{
			return createFormElementGroupPropertySource((FormElementGroup)obj, null);
		}

		if (obj instanceof BaseVisualFormEditor)
		{
			persist = ((BaseVisualFormEditor)obj).getForm();
			retargetToEditor = false;
		}
		else if (obj instanceof SimpleUserNode)
		{
			SimpleUserNode userNode = (SimpleUserNode)obj;
			if (userNode.isEnabled())
			{
				Object realObject = userNode.getRealObject();
				if (realObject instanceof ServoyProject)
				{
					persist = ((ServoyProject)realObject).getSolution();
					autoSave = true; // there is no editor, changes must be saved straight through
				}
				// some nodes are stored in an object array
				if (realObject instanceof Object[] && ((Object[])realObject).length > 0 && ((Object[])realObject)[0] instanceof IPersist)
				{
					realObject = ((Object[])realObject)[0];
				}
				if (realObject instanceof IPersist && !(realObject instanceof Solution) && !(realObject instanceof Style) &&
					!(realObject instanceof StringResource)) // solution is shown under ServoyProject nodes
				{
					persist = (IPersist)realObject;
				}
				if (persist instanceof IScriptElement)
				{
					autoSave = true;
				}
			}
		}
		else if (obj instanceof EditPart)
		{
			EditPart editPart = (EditPart)obj;
			Form contextForm = getEditpartFormContext(editPart);
			context = contextForm;
			Object model = editPart.getModel();

			if (model instanceof MobileListModel && key == IPropertySource.class)
			{
				return MobileListPropertySource.getMobileListPropertySource((MobileListModel)model, contextForm);
			}

			if (model instanceof IPersist)
			{
				persist = (IPersist)model;
				retargetToEditor = false;
			}
		}
		else if (obj instanceof PersistEditor)
		{
			PersistEditor editPart = (PersistEditor)obj;
			persist = editPart.getPersist();
			retargetToEditor = false;
		}
		else if (obj instanceof PersistContext)
		{
			persist = ((PersistContext)obj).getPersist();
			context = ((PersistContext)obj).getContext();
			retargetToEditor = false;
		}
		else if (obj instanceof IPersist)
		{
			persist = (IPersist)obj;
			autoSave = persist instanceof Solution || persist instanceof IScriptElement;
			// retargetToEditor must be true so that persist saves from anywhere (like quickfix)
			// retargetToEditor = false;
		}

		if (persist != null)
		{
			ServoyProject servoyProject = ServoyModelManager.getServoyModelManager().getServoyModel().getServoyProject(persist.getRootObject().getName());
			if (servoyProject == null)
			{
				ServoyLog.logError("Cannot find Servoy project for persist " + persist, null);
				return null;
			}
			// make sure we have the in-memory editing version, the real solution is read-only
			persist = AbstractRepository.searchPersist(servoyProject.getEditingSolution(), persist);
			if (persist == null)
			{
				// not present yet in editing solution
				return null;
			}
			if (context == null)
			{
				context = persist;
			}
			else
			{
				ServoyProject parentProject = servoyProject;
				if (!servoyProject.getSolution().getName().equals(context.getRootObject().getName()))
				{
					parentProject = ServoyModelManager.getServoyModelManager().getServoyModel().getServoyProject(context.getRootObject().getName());
				}
				if (parentProject == null)
				{
					ServoyLog.logError("Cannot find Servoy project for persist " + context, null); //$NON-NLS-1$
					return null;
				}
				context = AbstractRepository.searchPersist(parentProject.getEditingSolution(), context);
			}

			if (key == IPersist.class)
			{
				return persist;
			}
			if (key == IPropertySource.class)
			{
				// for properties view
				PersistContext persistContext = PersistContext.create(persist, context);
				boolean readonlyPersist = isReadonlyPersist(persist);
				PersistPropertySource persistProperties;
				Form form = (Form)persistContext.getContext().getAncestor(IRepository.FORMS);
				boolean mobile = form != null && form.getCustomMobileProperty(IMobileProperties.MOBILE_FORM.propertyName) != null;
				if (!mobile)
				{
					Solution solution = (Solution)persistContext.getContext().getAncestor(IRepository.SOLUTIONS);
					mobile = solution != null && solution.getSolutionType() == SolutionMetaData.MOBILE;
				}
				if (mobile)
				{
					// mobile form or mobile solution
					persistProperties = new MobilePersistPropertySource(persistContext, readonlyPersist);
				}
				else
				{
					// regular
					persistProperties = new PersistPropertySource(persistContext, readonlyPersist);
				}

				if (autoSave)
				{
					// all changes are saved immediately, on the persist node only (not recursive)
					return new SavingPersistPropertySource(persistProperties, servoyProject);
				}
				if (retargetToEditor)
				{
					// save actions are retargeted to the editor that handles the persist type
					return new RetargetToEditorPersistProperties(persistProperties);
				}
				return persistProperties;
			}
			if (key == IResource.class)
			{
				Pair<String, String> filePath = SolutionSerializer.getFilePath(persist, true);
				return ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(filePath.getLeft() + filePath.getRight()));
			}
			if (key == Openable.class)
			{
				return Openable.getOpenable(persist);
			}
		}

		return null;
	}

	private IPropertySource createFormElementGroupPropertySource(FormElementGroup group, Form context)
	{
		Form form = group.getParent();
		boolean mobile = form != null && form.getCustomMobileProperty(IMobileProperties.MOBILE_FORM.propertyName) != null;
		return mobile ? new MobileComponentWithTitlePropertySource(group, context) : new FormElementGroupPropertySource(group, context);
	}

	/**
	 * @param editPart
	 * @return
	 */
	private Form getEditpartFormContext(EditPart editPart)
	{
		EditPart formEditPart = editPart;
		while (formEditPart != null)
		{
			if (formEditPart instanceof IPersistEditPart)
			{
				IPersist persist = ((IPersistEditPart)formEditPart).getPersist();
				if (persist instanceof Form)
				{
					return (Form)persist;
				}
			}
			formEditPart = formEditPart.getParent();
		}
		return null;
	}

	/**
	 * Some persist types are not editable via the properties view.
	 * 
	 * @param persist
	 * @return
	 */
	protected boolean isReadonlyPersist(IPersist persist)
	{
		if (persist instanceof Media)
		{
			return true;
		}
		return false;
	}
}
