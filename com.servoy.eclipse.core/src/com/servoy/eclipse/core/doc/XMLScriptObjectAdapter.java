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
package com.servoy.eclipse.core.doc;

import java.util.HashSet;
import java.util.Set;

import com.servoy.j2db.documentaton.ParameterDocumentation;
import com.servoy.j2db.scripting.IScriptObject;
import com.servoy.j2db.util.Debug;

public class XMLScriptObjectAdapter implements IScriptObject
{
	private final IObjectDocumentation objDoc;
	private Class< ? >[] returnedTypes = null;
	private boolean returnedTypesComputed = false;

	public XMLScriptObjectAdapter(IObjectDocumentation objDoc)
	{
		this.objDoc = objDoc;
	}

	/**
	 * @param allReturnedTypes
	 */
	public void setReturnTypes(Class< ? >[] allReturnedTypes)
	{
		returnedTypes = allReturnedTypes;
		returnedTypesComputed = true;
	}

	public Class< ? >[] getAllReturnedTypes()
	{
		if (!returnedTypesComputed)
		{
			if (objDoc.getReturnedTypes().size() > 0)
			{
				Set<Class< ? >> returnedTypesSet = new HashSet<Class< ? >>();
				for (String className : objDoc.getReturnedTypes())
				{
					try
					{
						returnedTypesSet.add(Class.forName(className));
					}
					catch (ClassNotFoundException e)
					{
						Debug.error("Cannot load returned class '" + className + "' from documented object '" + objDoc.getQualifiedName() + "'."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
				}
				returnedTypes = new Class< ? >[returnedTypesSet.size()];
				returnedTypesSet.toArray(returnedTypes);
			}
			returnedTypesComputed = true;
		}
		return returnedTypes;
	}

	public String[] getParameterNames(String methodName)
	{
		IFunctionDocumentation fdoc = objDoc.getFunction(methodName);
		if (fdoc != null && fdoc.getArguments().size() > 0)
		{
			String[] argNames = new String[fdoc.getArguments().size()];
			int i = 0;
			for (ParameterDocumentation argDoc : fdoc.getArguments().values())
			{
				String name = argDoc.isOptional() ? "[" + argDoc.getName() + "]" : argDoc.getName(); //$NON-NLS-1$//$NON-NLS-2$
				argNames[i++] = name;
			}
			return argNames;
		}
		return null;
	}

	public String getSample(String methodName)
	{
		IFunctionDocumentation fdoc = objDoc.getFunction(methodName);
		if (fdoc != null) return fdoc.getSample();
		return null;
	}

	public String getToolTip(String methodName)
	{
		IFunctionDocumentation fdoc = objDoc.getFunction(methodName);
		if (fdoc != null) return fdoc.getDescription();
		return null;
	}

	public boolean isDeprecated(String methodName)
	{
		IFunctionDocumentation fdoc = objDoc.getFunction(methodName);
		if (fdoc != null) return fdoc.isDeprecated();
		return false;
	}
}
