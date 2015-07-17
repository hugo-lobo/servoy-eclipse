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

package com.servoy.eclipse.ui.property;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.ValuesConfig;
import org.sablo.specification.property.types.ValuesPropertyType;

import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IWebObject;
import com.servoy.j2db.persistence.StaticContentSpecLoader;
import com.servoy.j2db.server.ngclient.WebFormComponent;

/**
 * Sablo {@link PropertyDescription} based property source.
 *
 * @author acostescu
 */
public class PDPropertySource extends PersistPropertySource
{

	private final PropertyDescription propertyDescription;

	public PDPropertySource(PersistContext persistContext, boolean readonly, PropertyDescription propertyDescription)
	{
		super(persistContext, readonly);
		if (!(persistContext.getPersist() instanceof IWebObject))
		{
			throw new IllegalArgumentException();
		}
		this.propertyDescription = propertyDescription;
	}

	protected PropertyDescription getPropertyDescription()
	{
		return propertyDescription;
	}

	@Override
	protected Object getValueObject(FlattenedSolution flattenedEditingSolution, Form form)
	{
		return persistContext.getPersist();
	}

	@Override
	protected IPropertyHandler[] createPropertyHandlers(Object valueObject)
	{
		return createPropertyHandlersFromSpec(propertyDescription);
	}

	public static IPropertyHandler[] createPropertyHandlersFromSpec(PropertyDescription propertyDescription)
	{
		List<IPropertyHandler> props = new ArrayList<IPropertyHandler>();

		for (PropertyDescription desc : propertyDescription.getProperties().values())
		{
			Object scope = desc.getTag(WebFormComponent.TAG_SCOPE);
			if ("private".equals(scope) || "runtime".equals(scope))
			{
				// only show design properties
				continue;
			}

			List<Object> values = desc.getValues();
			if (values != null && values.size() > 0 && !desc.getName().equals(StaticContentSpecLoader.PROPERTY_STYLECLASS.getPropertyName()))
			{
				ValuesConfig config = new ValuesConfig();
				if (!(values.get(0) instanceof JSONObject))
				{
					config.setValues(values.toArray(new Object[0]));
				}
				else
				{
					List<String> displayValues = new ArrayList<String>();
					List<Object> realValues = new ArrayList<Object>();
					for (Object jsonObject : values)
					{
						if (jsonObject instanceof JSONObject && ((JSONObject)jsonObject).keys().hasNext())
						{
							String key = (String)((JSONObject)jsonObject).keys().next();
							displayValues.add(key);
							realValues.add(((JSONObject)jsonObject).opt(key));
						}
					}
					config.setValues(realValues.toArray(new Object[realValues.size()]), displayValues.toArray(new String[displayValues.size()]));
				}
				if (desc.getDefaultValue() != null)
				{
					config.addDefault(desc.getDefaultValue(), null);
				}
				props.add(new WebComponentPropertyHandler(new PropertyDescription(desc.getName(), ValuesPropertyType.INSTANCE, config, desc.getDefaultValue(),
					null, null, null, false)));
			}
			else
			{
				props.add(new WebComponentPropertyHandler(desc));
			}
		}

		return props.toArray(new IPropertyHandler[props.size()]);
	}

	@Override
	public String toString()
	{
		return propertyDescription.getName();
	}

}
