/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2012 Servoy BV

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

package com.servoy.eclipse.designer.editor;

import org.eclipse.draw2d.FigureListener;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Rectangle;

import com.servoy.j2db.persistence.Part;

/**
 * Temporary class used by mobile form editor to save location and size of edit part figure in underlying persist.
 * Added to be able to debug mobile forms in developer
 * 
 * @author rgansevles
 *
 */
public class SetBoundsToPartFigureListener implements FigureListener
{
	private final Part element;

	public SetBoundsToPartFigureListener(Part element)
	{
		this.element = element;
	}

	public void figureMoved(IFigure figure)
	{
		Rectangle bounds = figure.getBounds();
		if (bounds.y + bounds.height != element.getHeight())
		{
			element.setHeight(bounds.y + bounds.height);
		}
	}
}
