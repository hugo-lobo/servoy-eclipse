/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2022 Servoy BV

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebObjectSpecification;
import org.sablo.specification.property.CustomJSONArrayType;
import org.sablo.specification.property.CustomJSONObjectType;
import org.sablo.specification.property.IPropertyType;
import org.sablo.websocket.IServerService;

import com.servoy.eclipse.designer.editor.BaseVisualFormEditor;
import com.servoy.eclipse.model.ServoyModelFinder;
import com.servoy.eclipse.model.util.ModelUtils;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.ui.dialogs.autowizard.PropertyWizardDialogBuilder;
import com.servoy.eclipse.ui.property.PersistContext;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.persistence.IChildWebObject;
import com.servoy.j2db.persistence.ITable;
import com.servoy.j2db.persistence.WebComponent;
import com.servoy.j2db.persistence.WebCustomType;

/**
 * @author emera
 */
public class OpenPropertiesWizardHandler implements IServerService
{
	private final ISelectionProvider selectionProvider;
	private final BaseVisualFormEditor editorPart;

	public OpenPropertiesWizardHandler(BaseVisualFormEditor editorPart, ISelectionProvider selectionProvider)
	{
		this.editorPart = editorPart;
		this.selectionProvider = selectionProvider;
	}

	@Override
	public Object executeMethod(String methodName, JSONObject args)
	{
		PersistContext[] selection = null;
		if (selectionProvider != null)
		{
			selection = (PersistContext[])((IStructuredSelection)selectionProvider.getSelection()).toList().toArray(new PersistContext[0]);
		}
		if (selection.length >= 1)
		{
			PersistContext persistContext = selection[0];
			WebComponent webComponent = (WebComponent)persistContext.getPersist();
			WebObjectSpecification spec = WebComponentSpecProvider.getSpecProviderState()
				.getWebComponentSpecification(webComponent.getTypeName());
			Collection<String> allPropertiesNames = spec.getAllPropertiesNames();
			for (String propertyName : allPropertiesNames)
			{
				PropertyDescription property = spec.getProperty(propertyName);
				if (property != null)
				{
					if ("autoshow".equals(property.getTag("wizard")))
					{
						// prop type should be an array of a custom type..
						IPropertyType< ? > propType = property.getType();
						if (propType instanceof CustomJSONArrayType< ? , ? >)
						{

							CustomJSONObjectType< ? , ? > customObjectType = (CustomJSONObjectType< ? , ? >)((CustomJSONArrayType< ? , ? >)propType)
								.getCustomJSONTypeDefinition().getType();
							PropertyDescription customObjectDefinition = customObjectType.getCustomJSONTypeDefinition();
							Collection<PropertyDescription> wizardProperties = customObjectDefinition.getTaggedProperties("wizard");
							if (wizardProperties.size() > 0)
							{
								// feed this wizardProperties into the wizard
								System.err.println(wizardProperties);
								Display current = Display.getCurrent();
								if (current == null) current = Display.getDefault();

								FlattenedSolution flattenedSolution = ModelUtils.getEditingFlattenedSolution(webComponent);
								ITable table = ServoyModelFinder.getServoyModel().getDataSourceManager()
									.getDataSource(flattenedSolution.getFlattenedForm(editorPart.getForm()).getDataSource());

								List<Map<String, Object>> input = new ArrayList<>();
								Object prop = webComponent.getProperty(propertyName);
								Set<Object> previousColumns = new HashSet<>();
								if (prop instanceof IChildWebObject[])
								{
									IChildWebObject[] arr = (IChildWebObject[])prop;
									for (IChildWebObject obj : arr)
									{
										if (obj instanceof WebCustomType)
										{
											WebCustomType wct = (WebCustomType)obj;
											JSONObject object = (JSONObject)wct.getPropertiesMap().get("json");
											Map<String, Object> map = getAsMap(object);
											previousColumns.add(object.get("svyUUID"));
											input.add(map);
										}
									}
								}

								PropertyWizardDialogBuilder dialogBuilder = new PropertyWizardDialogBuilder(current.getActiveShell(), persistContext,
									flattenedSolution, property).withTable(table).withProperties(wizardProperties).withInput(input);
								if (dialogBuilder.open() != Window.OK) return null;
								List<Map<String, Object>> newProperties = dialogBuilder.getResult();

								Display.getDefault().asyncExec(() -> {
									editorPart.getCommandStack()
										.execute(new SetCustomArrayPropertiesCommand(propertyName, persistContext, newProperties, previousColumns));
								});
							}
							else
							{
								ServoyLog.logWarning("auto show wizard property " + property + " of custom type " + customObjectType +
									"\nhas no wizard properties\n" + propType, null);
							}
						}
						else
						{
							ServoyLog.logWarning("wizard:autoshow enabled for property " + property + " of component " + spec +
								" that is not an custom array type " + propType, null);
						}

					}
				}
			}
		}
		return null;
	}

	/**
	 * @param object
	 * @return
	 */
	private Map<String, Object> getAsMap(JSONObject object)
	{
		Map<String, Object> map = new HashMap<String, Object>();
		for (String key : object.keySet())
		{
			map.put(key, JSONObject.NULL.equals(object.get(key)) ? null : object.get(key));
		}
		return map;
	}
}
