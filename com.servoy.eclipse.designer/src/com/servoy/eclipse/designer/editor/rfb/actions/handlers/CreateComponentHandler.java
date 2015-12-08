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

package com.servoy.eclipse.designer.editor.rfb.actions.handlers;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentPackageSpecification;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebComponentSpecification;
import org.sablo.specification.WebLayoutSpecification;
import org.sablo.websocket.IServerService;

import com.servoy.base.persistence.constants.IRepositoryConstants;
import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.eclipse.core.elements.ElementFactory;
import com.servoy.eclipse.core.util.TemplateElementHolder;
import com.servoy.eclipse.designer.editor.BaseRestorableCommand;
import com.servoy.eclipse.designer.editor.BaseVisualFormEditor;
import com.servoy.eclipse.designer.editor.commands.AddContainerCommand;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.ui.property.PersistContext;
import com.servoy.eclipse.ui.util.ElementUtil;
import com.servoy.j2db.persistence.AbstractBase;
import com.servoy.j2db.persistence.AbstractContainer;
import com.servoy.j2db.persistence.BaseComponent;
import com.servoy.j2db.persistence.Bean;
import com.servoy.j2db.persistence.Field;
import com.servoy.j2db.persistence.FormElementGroup;
import com.servoy.j2db.persistence.GraphicalComponent;
import com.servoy.j2db.persistence.IBasicWebComponent;
import com.servoy.j2db.persistence.IDeveloperRepository;
import com.servoy.j2db.persistence.IFormElement;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.IRootObject;
import com.servoy.j2db.persistence.ISupportBounds;
import com.servoy.j2db.persistence.ISupportChilds;
import com.servoy.j2db.persistence.ISupportFormElements;
import com.servoy.j2db.persistence.IValidateName;
import com.servoy.j2db.persistence.LayoutContainer;
import com.servoy.j2db.persistence.Portal;
import com.servoy.j2db.persistence.PositionComparator;
import com.servoy.j2db.persistence.RectShape;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.StaticContentSpecLoader;
import com.servoy.j2db.persistence.Tab;
import com.servoy.j2db.persistence.TabPanel;
import com.servoy.j2db.persistence.Template;
import com.servoy.j2db.persistence.WebComponent;
import com.servoy.j2db.persistence.WebCustomType;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.UUID;

/**
 * @author user
 *
 */
public class CreateComponentHandler implements IServerService
{

	protected final ISelectionProvider selectionProvider;
	protected final BaseVisualFormEditor editorPart;
	private final AtomicInteger id = new AtomicInteger();

	public CreateComponentHandler(BaseVisualFormEditor editorPart, ISelectionProvider selectionProvider)
	{
		this.editorPart = editorPart;
		this.selectionProvider = selectionProvider;
	}

	/**
	 * @param methodName
	 * @param args
	 */
	public Object executeMethod(String methodName, final JSONObject args)
	{
		Display.getDefault().asyncExec(new Runnable()
		{
			public void run()
			{
				editorPart.getCommandStack().execute(new BaseRestorableCommand("createComponent")
				{
					private IPersist[] newPersist;

					@Override
					public void execute()
					{
						try
						{
							newPersist = createComponent(args);
							if (newPersist != null)
							{
								ServoyModelManager.getServoyModelManager().getServoyModel().firePersistsChanged(false, Arrays.asList(newPersist));
								IStructuredSelection structuredSelection = new StructuredSelection(newPersist);
								selectionProvider.setSelection(structuredSelection);
							}
						}
						catch (Exception ex)
						{
							Debug.error(ex);
						}
					}

					@Override
					public void undo()
					{
						try
						{
							if (newPersist != null)
							{
								for (IPersist iPersist : newPersist)
								{
									((IDeveloperRepository)iPersist.getRootObject().getRepository()).deleteObject(iPersist);
								}

								ServoyModelManager.getServoyModelManager().getServoyModel().firePersistsChanged(false, Arrays.asList(newPersist));
							}
						}
						catch (RepositoryException e)
						{
							ServoyLog.logError("Could not undo create elements", e);
						}
					}

				});
			}
		});
		return null;
	}

	/**
	* @param args
	* @throws JSONException
	* @throws RepositoryException
	*/
	protected IPersist[] createComponent(final JSONObject args) throws JSONException, RepositoryException
	{
		int x = args.getInt("x");
		int y = args.getInt("y");
		int w = args.optInt("w");
		int h = args.optInt("h");
		if (args.has("type"))
		{
			// a ghost dragged from the pallete. it is defined in the "types" section of the .spec file
			IPersist next = PersistFinder.INSTANCE.searchForPersist(editorPart, (String)args.get("dropTargetUUID"));
			if (next instanceof BaseComponent)
			{
				if (next instanceof IBasicWebComponent)
				{
					IBasicWebComponent parentBean = (IBasicWebComponent)next;
					String propertyName = args.getString("propertyName");
					String compName = "bean_" + id.incrementAndGet();
					while (!PersistFinder.INSTANCE.checkName(editorPart, compName))
					{
						compName = "bean_" + id.incrementAndGet();
					}
					WebCustomType bean = AddContainerCommand.addCustomType(parentBean, propertyName, compName);
					return new IPersist[] { bean };
				}
				else if (args.getString("type").equals("tab"))
				{
					if (next instanceof ISupportChilds)
					{
						ISupportChilds iSupportChilds = (ISupportChilds)next;
						Tab newTab = (Tab)editorPart.getForm().getRootObject().getChangeHandler().createNewObject(iSupportChilds, IRepository.TABS);
						String tabName = "tab_" + id.incrementAndGet();
						while (!PersistFinder.INSTANCE.checkName(editorPart, tabName))
						{
							tabName = "tab_" + id.incrementAndGet();
						}
						newTab.setText(tabName);
						newTab.setLocation(new Point(x, y));
						iSupportChilds.addChild(newTab);
						return new IPersist[] { newTab };
					}
				}
			}
		}
		else if (args.has("name"))
		{
			ISupportFormElements parent = editorPart.getForm();
			if (args.has("dropTargetUUID"))
			{

				IPersist searchForPersist = PersistFinder.INSTANCE.searchForPersist(editorPart, args.getString("dropTargetUUID"));
				if (searchForPersist != null)
				{
					searchForPersist = ElementUtil.getOverridePersist(PersistContext.create(searchForPersist, editorPart.getForm()));
				}
				if (searchForPersist != null)
				{
					IPersist p = searchForPersist;
					while (!(p instanceof ISupportFormElements) && p != null)
					{
						p = p.getParent();
					}
					if (p instanceof ISupportFormElements)
					{
						parent = (ISupportFormElements)p;
					}
				}
			}
			if (editorPart.getForm().isResponsiveLayout())
			{
				List<IPersist> children = new ArrayList<IPersist>();
				Iterator<IPersist> it = parent.getAllObjects();
				while (it.hasNext())
				{
					IPersist persist = it.next();
					if (persist instanceof ISupportBounds)
					{
						children.add(persist);
					}
				}

				// default place it as the first element.
				x = 1;
				y = 1;
				if (children.size() > 0)
				{
					IPersist[] childArray = children.toArray(new IPersist[0]);
					Arrays.sort(childArray, PositionComparator.XY_PERSIST_COMPARATOR);
					if (args.has("rightSibling"))
					{
						IPersist rightSibling = PersistFinder.INSTANCE.searchForPersist(editorPart, args.optString("rightSibling", null));
						int counter = 1;

						for (IPersist element : childArray)
						{
							if (element == rightSibling)
							{
								x = counter;
								y = counter;
								counter++;
							}
							((ISupportBounds)element).setLocation(new Point(counter, counter));
							counter++;
						}
					}
					else
					{
						// insert as last element in flow layout because no right/bottom sibling was given
						Point location = ((ISupportBounds)childArray[childArray.length - 1]).getLocation();
						x = location.x + 1;
						y = location.y + 1;
					}
				}
			}
			String name = args.getString("name");
			if ("servoydefault-button".equals(name))
			{
				GraphicalComponent gc = parent.createNewGraphicalComponent(new Point(x, y));
				gc.setText("button");
				gc.setOnActionMethodID(-1);
				gc.setSize(new Dimension(w, h));
				return new IPersist[] { gc };
			}
			else if ("servoydefault-label".equals(name))
			{
				GraphicalComponent gc = parent.createNewGraphicalComponent(new Point(x, y));
				gc.setText("label");
				gc.setSize(new Dimension(w, h));
				return new IPersist[] { gc };
			}
			else if ("servoydefault-combobox".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.COMBOBOX);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-textfield".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.TEXT_FIELD);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-textarea".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.TEXT_AREA);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-password".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.PASSWORD);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-calendar".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.CALENDAR);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-typeahead".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.TYPE_AHEAD);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-spinner".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.SPINNER);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-check".equals(name) || "servoydefault-checkgroup".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.CHECKS);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-radio".equals(name) || "servoydefault-radiogroup".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.RADIOS);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-imagemedia".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.IMAGE_MEDIA);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-listbox".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.LIST_BOX);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-htmlarea".equals(name))
			{
				Field field = parent.createNewField(new Point(x, y));
				field.setDisplayType(Field.HTML_AREA);
				field.setEditable(true);
				field.setSize(new Dimension(w, h));
				return new IPersist[] { field };
			}
			else if ("servoydefault-tabpanel".equals(name))
			{
				String compName = "tabpanel_" + id.incrementAndGet();
				while (!PersistFinder.INSTANCE.checkName(editorPart, compName))
				{
					compName = "tabpanel_" + id.incrementAndGet();
				}
				TabPanel tabPanel = null;
				if (parent instanceof AbstractContainer)
				{
					tabPanel = ((AbstractContainer)parent).createNewTabPanel(compName);
				}
				else
				{
					tabPanel = editorPart.getForm().createNewTabPanel(compName);
				}
				tabPanel.setLocation(new Point(x, y));
				tabPanel.setSize(new Dimension(w, h));
				return new IPersist[] { tabPanel };
			}
			else if ("servoydefault-splitpane".equals(name))
			{
				String compName = "tabpanel_" + id.incrementAndGet();
				while (!PersistFinder.INSTANCE.checkName(editorPart, compName))
				{
					compName = "tabpanel_" + id.incrementAndGet();
				}
				TabPanel tabPanel = null;
				if (parent instanceof AbstractContainer)
				{
					tabPanel = ((AbstractContainer)parent).createNewTabPanel(compName);
				}
				else
				{
					tabPanel = editorPart.getForm().createNewTabPanel(compName);
				}
				tabPanel.setLocation(new Point(x, y));
				tabPanel.setTabOrientation(TabPanel.SPLIT_HORIZONTAL);
				tabPanel.setSize(new Dimension(w, h));
				return new IPersist[] { tabPanel };
			}
			else if ("servoycore-portal".equals(name))
			{
				String compName = "portal_" + id.incrementAndGet();
				while (!PersistFinder.INSTANCE.checkName(editorPart, compName))
				{
					compName = "portal_" + id.incrementAndGet();
				}
				Portal portal = null;
				if (parent instanceof AbstractContainer)
				{
					portal = ((AbstractContainer)parent).createNewPortal(compName, new Point(x, y));
				}
				else
				{
					portal = editorPart.getForm().createNewPortal(compName, new Point(x, y));
				}
				portal.setSize(new Dimension(w, h));
				return new IPersist[] { portal };
			}
			else if ("servoydefault-rectangle".equals(name))
			{
				RectShape shape = editorPart.getForm().createNewRectangle(new Point(x, y));
				shape.setLineSize(1);
				shape.setSize(new Dimension(w, h));
				return new IPersist[] { shape };
			}
			else
			{
				WebComponentSpecification spec = WebComponentSpecProvider.getInstance().getWebComponentSpecification(name);
				if (spec != null)
				{
					String compName = null;
					String componentName = name;
					int index = componentName.indexOf("-");
					if (index != -1)
					{
						componentName = componentName.substring(index + 1);
					}
					componentName = componentName.replaceAll("-", "_");
					compName = componentName + "_" + id.incrementAndGet();
					while (!PersistFinder.INSTANCE.checkName(editorPart, compName))
					{
						compName = componentName + "_" + id.incrementAndGet();
					}

					WebComponent webComponent = null;
					if (parent instanceof Portal)
					{
						Portal portal = (Portal)parent;
						webComponent = (WebComponent)editorPart.getForm().getRootObject().getChangeHandler().createNewObject(portal, IRepository.WEBCOMPONENTS);
						webComponent.setProperty("text", compName);
						webComponent.setTypeName(name);
						portal.addChild(webComponent);
					}
					else if (parent instanceof AbstractContainer)
					{
						webComponent = ((AbstractContainer)parent).createNewWebComponent(compName, name);

					}
					else if (parent instanceof Bean)
					{
						// TODO create it inthe bean an store it in the component array???
					}
					webComponent.setLocation(new Point(x, y));
					webComponent.setSize(new Dimension(w, h));
					PropertyDescription description = spec.getProperty(StaticContentSpecLoader.PROPERTY_SIZE.getPropertyName());
					if (description != null && description.getDefaultValue() instanceof JSONObject)
					{
						webComponent.setSize(new Dimension(((JSONObject)description.getDefaultValue()).optInt("width", 80),
							((JSONObject)description.getDefaultValue()).optInt("height", 80)));
					}
					return new IPersist[] { webComponent };
				}
				else
				{
					WebComponentPackageSpecification<WebLayoutSpecification> specifications = WebComponentSpecProvider.getInstance().getLayoutSpecifications().get(
						args.optString("packageName"));
					if (specifications != null)
					{
						WebLayoutSpecification layoutSpec = specifications.getSpecification(name);
						if (layoutSpec != null)
						{
							JSONObject config = layoutSpec.getConfig() instanceof String ? new JSONObject((String)layoutSpec.getConfig()) : null;
							return new IPersist[] { createLayoutContainer(parent, layoutSpec, config, x, specifications, args.optString("packageName")) };
						}
					}
					else
					{
						for (IRootObject template : ServoyModelManager.getServoyModelManager().getServoyModel().getActiveRootObjects(IRepository.TEMPLATES))
						{
							if (template.getName().equals(name))
							{
								Object[] applyTemplate = ElementFactory.applyTemplate(parent, new TemplateElementHolder((Template)template),
									new org.eclipse.swt.graphics.Point(x, y), false);
								if (applyTemplate.length > 0)
								{
									if (applyTemplate[0] instanceof FormElementGroup)
									{
										Iterator<IFormElement> elements = ((FormElementGroup)applyTemplate[0]).getElements();
										//convert iterator to []
										ArrayList<IFormElement> list = new ArrayList<>();
										while (elements.hasNext())
										{
											IFormElement next = elements.next();
											list.add(next);
										}
										return list.toArray(new IPersist[list.size()]);
									}
									else
									{ //Object[] to IPersist[]
										return Arrays.asList(applyTemplate).toArray(new IPersist[applyTemplate.length]);
									}
								}
							}
						}
					}
				}
			}
		}
		else if (args.has("uuid"))
		{
			IPersist persist = editorPart.getForm().getChild(UUID.fromString(args.getString("uuid")));
			if (persist instanceof AbstractBase)
			{
				IValidateName validator = ServoyModelManager.getServoyModelManager().getServoyModel().getNameValidator();
				IPersist newPersist = ((AbstractBase)persist).cloneObj(persist.getParent(), true, validator, true, true, true);
				((ISupportBounds)newPersist).setLocation(new Point(x, y));
				if (w > 0 && h > 0) ((ISupportBounds)newPersist).setSize(new Dimension(w, h));
				return new IPersist[] { newPersist };
			}
		}
		return null;
	}

	/**
	 * @param parent
	 * @param layoutSpec
	 * @return
	 * @throws RepositoryException
	 * @throws JSONException
	 */
	protected IPersist createLayoutContainer(ISupportFormElements parent, WebLayoutSpecification layoutSpec, JSONObject config, int index,
		WebComponentPackageSpecification<WebLayoutSpecification> specifications, String packageName) throws RepositoryException, JSONException
	{
		Iterator<IPersist> childContainersIte = parent.getObjects(IRepositoryConstants.LAYOUTCONTAINERS);
		LayoutContainer sameTypeChildContainer = null;
		while (childContainersIte.hasNext())
		{
			LayoutContainer childContainer = (LayoutContainer)childContainersIte.next();
			if (layoutSpec.getName().equals(childContainer.getSpecName()))
			{
				sameTypeChildContainer = childContainer;
			}
		}
		LayoutContainer container = (LayoutContainer)editorPart.getForm().getRootObject().getChangeHandler().createNewObject(parent,
			IRepository.LAYOUTCONTAINERS);
		container.setSpecName(layoutSpec.getName());
		container.setPackageName(packageName);
		parent.addChild(container);
		container.setLocation(new Point(index, index));
		if (config != null)
		{
			// if this is a composite try to set the actual layoutname (so a row combination with columns becomes here just a row)
			String layoutName = config.optString("layoutName", null);
			if (layoutName != null) container.setSpecName(layoutName);
			Iterator keys = config.keys();
			while (keys.hasNext())
			{
				String key = (String)keys.next();
				Object value = config.get(key);
				if ("children".equals(key))
				{
					// special key to create children instead of a attribute set.
					JSONArray array = (JSONArray)value;
					for (int i = 0; i < array.length(); i++)
					{
						JSONObject jsonObject = array.getJSONObject(i);
						if (jsonObject.has("layoutName"))
						{
							WebLayoutSpecification spec = specifications.getSpecification(jsonObject.getString("layoutName"));
							createLayoutContainer(container, spec, jsonObject.optJSONObject("model"), i + 1, specifications, packageName);
						}
						else if (jsonObject.has("componentName"))
						{
							String compName = "bean_" + id.incrementAndGet();
							container.createNewWebComponent(compName, jsonObject.getString("componentName"));
						}
					}
				} // children and layoutName are special
				else if (!"layoutName".equals(key))
				{
					container.putAttribute(key, sameTypeChildContainer != null ? sameTypeChildContainer.getAttribute(key) : value.toString());
				}
			}
		}
		return container;
	}
}
