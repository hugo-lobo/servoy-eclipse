package com.servoy.eclipse.designer.editor.commands;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.PackageSpecification;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebLayoutSpecification;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.property.ICustomType;
import org.sablo.websocket.utils.PropertyUtils;

import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.eclipse.core.elements.ElementFactory;
import com.servoy.eclipse.core.util.TemplateElementHolder;
import com.servoy.eclipse.core.util.UIUtils;
import com.servoy.eclipse.designer.editor.BaseRestorableCommand;
import com.servoy.eclipse.designer.editor.BaseVisualFormEditor;
import com.servoy.eclipse.designer.editor.BaseVisualFormEditorDesignPage;
import com.servoy.eclipse.designer.editor.rfb.RfbVisualFormEditorDesignPage;
import com.servoy.eclipse.designer.editor.rfb.actions.handlers.PersistFinder;
import com.servoy.eclipse.designer.util.DesignerUtil;
import com.servoy.eclipse.model.util.ModelUtils;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.ui.dialogs.FlatTreeContentProvider;
import com.servoy.eclipse.ui.dialogs.TreePatternFilter;
import com.servoy.eclipse.ui.dialogs.TreeSelectDialog;
import com.servoy.eclipse.ui.property.PersistContext;
import com.servoy.eclipse.ui.util.ElementUtil;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.persistence.AbstractBase;
import com.servoy.j2db.persistence.AbstractContainer;
import com.servoy.j2db.persistence.CSSPositionUtils;
import com.servoy.j2db.persistence.FormElementGroup;
import com.servoy.j2db.persistence.IBasicWebComponent;
import com.servoy.j2db.persistence.IChildWebObject;
import com.servoy.j2db.persistence.IDeveloperRepository;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.ISupportBounds;
import com.servoy.j2db.persistence.ISupportChilds;
import com.servoy.j2db.persistence.ISupportFormElements;
import com.servoy.j2db.persistence.LayoutContainer;
import com.servoy.j2db.persistence.NameComparator;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.WebComponent;
import com.servoy.j2db.persistence.WebCustomType;
import com.servoy.j2db.server.ngclient.property.types.NGCustomJSONObjectType;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.PersistHelper;
import com.servoy.j2db.util.Utils;


public class AddContainerCommand extends AbstractHandler implements IHandler
{
	public static final String COMMAND_ID = "com.servoy.eclipse.designer.rfb.add";

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException
	{
		try
		{
			final BaseVisualFormEditor activeEditor = DesignerUtil.getActiveEditor();
			if (activeEditor != null)
			{
				final ISelectionProvider selectionProvider = activeEditor.getSite().getSelectionProvider();
				PersistContext persistCtxt = null;
				if (DesignerUtil.getContentOutlineSelection() != null)
				{
					persistCtxt = DesignerUtil.getContentOutlineSelection();
				}
				else
				{
					IStructuredSelection sel = (IStructuredSelection)selectionProvider.getSelection();
					if (!sel.isEmpty())
					{
						Object[] selection = sel.toArray();
						persistCtxt = selection[0] instanceof PersistContext ? (PersistContext)selection[0] : PersistContext.create((IPersist)selection[0]);
					}
				}

				if (persistCtxt != null)
				{
					PersistContext persistContext = persistCtxt;

					Object dlgSelection = null;
					if (event.getParameter("com.servoy.eclipse.designer.editor.rfb.menu.add.template") != null)
					{
						if (persistContext.getPersist() instanceof AbstractContainer)
						{
							TreeSelectDialog dialog = new TreeSelectDialog(activeEditor.getEditorSite().getShell(), true, true, TreePatternFilter.FILTER_LEAFS,
								FlatTreeContentProvider.INSTANCE, new LabelProvider()
								{
									@Override
									public String getText(Object element)
									{
										return ((TemplateElementHolder)element).template.getName();
									};
								}, null, null, SWT.NONE, "Select template",
								DesignerUtil.getResponsiveLayoutTemplates((AbstractContainer)persistContext.getPersist()), null, false, "TemplateDialog", null);
							if (dialog.open() == Window.CANCEL)
							{
								return null;
							}
							dlgSelection = ((StructuredSelection)dialog.getSelection()).getFirstElement();
						}
					}
					else if (event.getParameters().isEmpty())
					{
						//we need to ask for component spec
						List<WebObjectSpecification> specs = new ArrayList<WebObjectSpecification>();
						WebObjectSpecification[] webComponentSpecifications = WebComponentSpecProvider.getSpecProviderState()
							.getAllWebComponentSpecifications();
						for (WebObjectSpecification webComponentSpec : webComponentSpecifications)
						{
							if (webComponentSpec.isDeprecated()) continue;
							if (!webComponentSpec.getPackageName().equals("servoydefault"))
							{
								specs.add(webComponentSpec);
							}
						}
						LabelProvider labelProvider = new LabelProvider()
						{
							@Override
							public String getText(Object element)
							{
								String displayName = ((WebObjectSpecification)element).getDisplayName();
								if (Utils.stringIsEmpty(displayName))
								{
									displayName = ((WebObjectSpecification)element).getName();
									int index = displayName.indexOf("-");
									if (index != -1)
									{
										displayName = displayName.substring(index + 1);
									}
								}
								return displayName + " [" + ((WebObjectSpecification)element).getPackageName() + "]";
							};
						};
						Collections.sort(specs, new Comparator<WebObjectSpecification>()
						{

							@Override
							public int compare(WebObjectSpecification o1, WebObjectSpecification o2)
							{
								return NameComparator.INSTANCE.compare(labelProvider.getText(o1), labelProvider.getText(o2));
							}
						});
						TreeSelectDialog dialog = new TreeSelectDialog(activeEditor.getEditorSite().getShell(), true, true, TreePatternFilter.FILTER_LEAFS,
							FlatTreeContentProvider.INSTANCE, labelProvider, null, null, SWT.NONE, "Select spec", specs.toArray(new WebObjectSpecification[0]),
							null, false, "SpecDialog", null);
						if (dialog.open() == Window.CANCEL)
						{
							return null;
						}
						dlgSelection = ((StructuredSelection)dialog.getSelection()).getFirstElement();
					}


					Object dialogSelection = dlgSelection;
					activeEditor.getCommandStack().execute(new BaseRestorableCommand("createLayoutContainer")
					{
						private IPersist persist;

						@Override
						public void execute()
						{
							try
							{
								if (event.getParameter("com.servoy.eclipse.designer.editor.rfb.menu.customtype.property") != null)
								{
									if (persistContext.getPersist() instanceof IBasicWebComponent)
									{
										IBasicWebComponent parentBean = (IBasicWebComponent)ElementUtil.getOverridePersist(persistContext);
										addCustomType(parentBean, event.getParameter("com.servoy.eclipse.designer.editor.rfb.menu.customtype.property"), null,
											-1);
										persist = parentBean;
									}
								}
								else if (event.getParameter("com.servoy.eclipse.designer.editor.rfb.menu.add.spec") != null)
								{
									String specName = event.getParameter("com.servoy.eclipse.designer.editor.rfb.menu.add.spec");
									String packageName = event.getParameter("com.servoy.eclipse.designer.editor.rfb.menu.add.package");
									persist = addLayoutComponent(persistContext, specName, packageName,
										new JSONObject(event.getParameter("com.servoy.eclipse.designer.editor.rfb.menu.add.config")),
										computeNextLayoutContainerIndex(persistContext.getPersist()));
								}
								else if (event.getParameter("com.servoy.eclipse.designer.editor.rfb.menu.add.template") != null)
								{
									AbstractContainer parentPersist = (AbstractContainer)ElementUtil.getOverridePersist(persistContext);
									int x = parentPersist.getAllObjectsAsList().size();
									TemplateElementHolder template = (TemplateElementHolder)dialogSelection;
									Object[] applyTemplate = ElementFactory.applyTemplate(parentPersist, template,
										new org.eclipse.swt.graphics.Point(x + 1, x + 1), false);
									if (applyTemplate.length > 0)
									{
										List<IPersist> persists = new ArrayList<>();
										for (Object o : applyTemplate)
										{
											if (o instanceof FormElementGroup)
											{
												FormElementGroup group = (FormElementGroup)o;
												group.getElements().forEachRemaining(persists::add);
											}
											else if (o instanceof IPersist)
											{
												persists.add((IPersist)o);
											}
										}
										ServoyModelManager.getServoyModelManager().getServoyModel().firePersistsChanged(false, persists);
										IStructuredSelection structuredSelection = new StructuredSelection(persists.size() > 0 ? persists.get(0) : persists);
										selectionProvider.setSelection(structuredSelection);
										persist = parentPersist;
									}
								}
								else
								{
									AbstractContainer parentPersist = (AbstractContainer)ElementUtil.getOverridePersist(persistContext);
									WebObjectSpecification spec = (WebObjectSpecification)dialogSelection;
									String componentName = spec.getName();
									int index = componentName.indexOf("-");
									if (index != -1)
									{
										componentName = componentName.substring(index + 1);
									}
									componentName = componentName.replaceAll("-", "_");
									String baseName = componentName;
									int i = 1;
									while (!PersistFinder.INSTANCE.checkName(activeEditor, componentName))
									{
										componentName = baseName + "_" + i;
										i++;
									}
									persist = parentPersist.createNewWebComponent(componentName, spec.getName());

									if (activeEditor.getForm().isResponsiveLayout())
									{
										int maxLocation = 0;
										ISupportChilds parent = PersistHelper.getFlattenedPersist(ModelUtils.getEditingFlattenedSolution(persist),
											activeEditor.getForm(), parentPersist);
										Iterator<IPersist> it = parent.getAllObjects();
										while (it.hasNext())
										{
											IPersist currentPersist = it.next();
											if (currentPersist != persist && currentPersist instanceof ISupportBounds)
											{
												Point location = ((ISupportBounds)currentPersist).getLocation();
												if (location.x > maxLocation) maxLocation = location.x;
												if (location.y > maxLocation) maxLocation = location.y;
											}
										}
										((WebComponent)persist).setLocation(new Point(maxLocation + 1, maxLocation + 1));
									}
									Collection<String> allPropertiesNames = spec.getAllPropertiesNames();
									for (String string : allPropertiesNames)
									{
										PropertyDescription property = spec.getProperty(string);
										if (property != null && property.getInitialValue() != null)
										{
											Object initialValue = property.getInitialValue();
											if (initialValue != null) ((WebComponent)persist).setProperty(string, initialValue);
										}
									}
								}
								if (persist != null)
								{
									List<IPersist> changes = new ArrayList<>();
									if (!persistContext.getPersist().getUUID().equals(persist.getParent().getUUID()))
									{
										ISupportChilds parent = persist.getParent();
										changes.add(persist.getParent());

										FlattenedSolution flattenedSolution = ModelUtils.getEditingFlattenedSolution(persist);
										parent = PersistHelper.getFlattenedPersist(flattenedSolution, activeEditor.getForm(), parent);
										Iterator<IPersist> it = parent.getAllObjects();
										while (it.hasNext())
										{
											// why do we need to override all siblings here ?
											ElementUtil.getOverridePersist(PersistContext.create(it.next(), activeEditor.getForm()));
										}
									}
									else
									{
										changes.add(persist);
									}
									ServoyModelManager.getServoyModelManager().getServoyModel().firePersistsChanged(false, changes);
									Object[] selection = new Object[] { PersistContext.create(persist, persistContext.getContext()) };
									final IStructuredSelection structuredSelection = new StructuredSelection(selection);
									// wait for tree to be refreshed with new element
									Display.getDefault().asyncExec(new Runnable()
									{
										@Override
										public void run()
										{
											Display.getDefault().asyncExec(new Runnable()
											{
												@Override
												public void run()
												{
													if (DesignerUtil.getContentOutline() != null)
													{
														DesignerUtil.getContentOutline().setSelection(structuredSelection);
													}
													else
													{
														selectionProvider.setSelection(structuredSelection);
													}
													if (persist instanceof LayoutContainer &&
														CSSPositionUtils.isCSSPositionContainer((LayoutContainer)persist))
													{
														if (org.eclipse.jface.dialogs.MessageDialog.openQuestion(UIUtils.getActiveShell(),
															"Edit css position container",
															"Do you want to zoom into the layout container so you can edit it ?"))
														{
															BaseVisualFormEditor editor = DesignerUtil.getActiveEditor();
															if (editor != null)
															{
																BaseVisualFormEditorDesignPage activePage = editor.getGraphicaleditor();
																if (activePage instanceof RfbVisualFormEditorDesignPage)
																	((RfbVisualFormEditorDesignPage)activePage).showContainer((LayoutContainer)persist);
															}
														}
													}
												}
											});

										}
									});
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
								if (persist != null)
								{
									((IDeveloperRepository)persist.getRootObject().getRepository()).deleteObject(persist);
									ServoyModelManager.getServoyModelManager().getServoyModel().firePersistsChanged(false,
										Arrays.asList(new IPersist[] { persist }));
								}
							}
							catch (RepositoryException e)
							{
								ServoyLog.logError("Could not undo create layout container", e);
							}
						}
					});
				}
			}
		}
		catch (NullPointerException npe)
		{
			Debug.log(npe);
		}
		return null;
	}

	private LayoutContainer addLayoutComponent(PersistContext parentPersist, String specName, String packageName, JSONObject configJson, int index)
	{
		LayoutContainer container;
		try
		{
			if (parentPersist != null && parentPersist.getPersist() instanceof AbstractBase && parentPersist.getPersist() instanceof ISupportChilds)
			{
				AbstractBase parent = (AbstractBase)ElementUtil.getOverridePersist(parentPersist);
				PackageSpecification<WebLayoutSpecification> specifications = WebComponentSpecProvider.getSpecProviderState().getLayoutSpecifications().get(
					packageName);
				container = (LayoutContainer)parent.getRootObject().getChangeHandler().createNewObject(((ISupportChilds)parent), IRepository.LAYOUTCONTAINERS);
				container.setSpecName(specName);
				container.setPackageName(packageName);
				parent.addChild(container);
				container.setLocation(new Point(index, index));
				if (CSSPositionUtils.isCSSPositionContainer(container)) container.setSize(new Dimension(200, 200));
				if (configJson != null)
				{
					Iterator keys = configJson.keys();
					while (keys.hasNext())
					{
						String key = (String)keys.next();
						Object value = configJson.get(key);
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
									addLayoutComponent(PersistContext.create(container, parentPersist.getContext()), spec.getName(), packageName,
										jsonObject.optJSONObject("model"), i + 1);
								}
								else if (jsonObject.has("componentName"))
								{
									WebComponent component = (WebComponent)parent.getRootObject().getChangeHandler().createNewObject(((ISupportChilds)parent),
										IRepository.WEBCOMPONENTS);
									component.setLocation(new Point(i + 1, i + 1));
									component.setTypeName(jsonObject.getString("componentName"));
									((AbstractBase)container).addChild(component);
								}
							}
						} // children and layoutName are special
						else if (!"layoutName".equals(key))
						{
							container.putAttribute(key, value.toString());
						}
						else if ("layoutName".equals(key))
						{
							container.setSpecName(value.toString());
						}
					}
					return container;
				}
			}
		}
		catch (RepositoryException e)
		{
			Debug.log(e);
		}
		catch (JSONException e)
		{
			Debug.log(e);
		}
		return null;
	}

	private int computeNextLayoutContainerIndex(IPersist parent)
	{
		int i = 1;
		if (parent instanceof ISupportFormElements)
		{
			Iterator<IPersist> allObjects = ((ISupportFormElements)parent).getAllObjects();

			while (allObjects.hasNext())
			{
				IPersist child = allObjects.next();
				if (child instanceof AbstractContainer)
				{
					i++;
				}
			}
			return i;
		}
		return i;
	}

	public static WebCustomType addCustomType(IBasicWebComponent parentBean, String propertyName, String compName, int arrayIndex)
	{
		int index = arrayIndex;
		WebObjectSpecification spec = WebComponentSpecProvider.getSpecProviderState().getWebComponentSpecification(parentBean.getTypeName());
		boolean isArray = spec.isArrayReturnType(propertyName);
		PropertyDescription targetPD = spec.getProperty(propertyName);
		String typeName = PropertyUtils.getSimpleNameOfCustomJSONTypeProperty(targetPD.getType());
		IChildWebObject[] arrayValue = null;
		if (isArray)
		{
			targetPD = ((ICustomType< ? >)targetPD.getType()).getCustomJSONTypeDefinition();
			if (parentBean instanceof WebComponent)
			{
				arrayValue = (IChildWebObject[])((WebComponent)parentBean).getProperty(propertyName);
			}
			if (index == -1) index = arrayValue != null ? arrayValue.length : 0;
		}
		if (parentBean instanceof WebComponent)
		{
			WebComponent parentWebComponent = (WebComponent)parentBean;
			WebCustomType customType = WebCustomType.createNewInstance(parentWebComponent, targetPD, propertyName, index, true);
			customType.setName(compName);
			customType.setTypeName(typeName);

			if (targetPD.getType() instanceof NGCustomJSONObjectType)
			{
				Collection<String> allPropertiesNames = ((NGCustomJSONObjectType)targetPD.getType()).getCustomJSONTypeDefinition().getAllPropertiesNames();
				for (String string : allPropertiesNames)
				{
					PropertyDescription property = ((NGCustomJSONObjectType)targetPD.getType()).getCustomJSONTypeDefinition().getProperty(string);
					if (property != null && property.getInitialValue() != null)
					{
						Object initialValue = property.getInitialValue();
						if (initialValue != null) customType.setProperty(string, initialValue);
					}
				}
			}

			parentWebComponent.insertChild(customType); // if it is array it will make use of index given above in WebCustomType.createNewInstance(...) when inserting; otherwise it's a simple set to some property name anyway

			return customType;
		}
		return null;
	}
}
