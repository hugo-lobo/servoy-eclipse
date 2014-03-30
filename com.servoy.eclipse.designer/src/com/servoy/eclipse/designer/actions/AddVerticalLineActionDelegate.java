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
package com.servoy.eclipse.designer.actions;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.SwingConstants;

import com.servoy.eclipse.ui.property.PersistPropertyHandler;
import com.servoy.j2db.server.ngclient.property.PropertyType;
import com.servoy.j2db.util.gui.SpecialMatteBorder;

/**
 * Add a label with some special settings so that it appears as a vertical line.
 * 
 * @author rgansevles
 * 
 */

public class AddVerticalLineActionDelegate extends AddLabelActionDelegate
{

	public AddVerticalLineActionDelegate()
	{
		addSetPropertyValue("text", "");
		addSetPropertyValue("borderType", new SpecialMatteBorder(0, 1, 0, 0, Color.black, Color.black, Color.black, Color.black));
		addSetPropertyValue(
			"horizontalAlignment",
			Integer.valueOf(((PropertyType.ValuesConfig)PersistPropertyHandler.HORIZONTAL_ALIGNMENT_VALUES.getConfig()).getRealIndexOf(Integer.valueOf(SwingConstants.RIGHT))));
		addSetPropertyValue("transparent", Boolean.TRUE);
		addSetPropertyValue("size", new Dimension(1, 40));
	}
}
