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
package com.servoy.eclipse.ui.property;

import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.sablo.websocket.utils.JSONUtils;

/**
 * Property descriptor wrapper that implements IPropertySetter.
 *
 */
public abstract class PropertySetterDelegatePropertyController<P, S extends PersistPropertySource> extends DelegatePropertySetterController<P, S>
{
	public PropertySetterDelegatePropertyController(IPropertyDescriptor propertyDescriptor, Object id)
	{
		super(propertyDescriptor, id);
	}

	@SuppressWarnings("unchecked")
	public P getProperty(S propertySource)
	{
		return (P)propertySource.getPersistPropertyValue(getId());
	}

	@Override
	public void resetPropertyValue(S propertySource)
	{
		propertySource.setPersistPropertyValue(getId(), propertySource.getDefaultPersistValue(getId()));
	}

	@Override
	public boolean isPropertySet(S propertySource)
	{
		Object defaultValue = propertySource.getDefaultPersistValue(getId());
		Object propertyValue = propertySource.getPersistPropertyValue(getId());
		return defaultValue != propertyValue &&
			(defaultValue == null || !(defaultValue.equals(propertyValue) || JSONUtils.areEqual(defaultValue, propertyValue)));
	}
}
