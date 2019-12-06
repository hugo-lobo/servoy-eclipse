/*******************************************************************************
 * Copyright (c) 2004, 2015 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jface.viewers;

import org.eclipse.swt.graphics.Font;

/**
 * Interface to provide font representation for a given element.
 * @see org.eclipse.jface.viewers.IFontDecorator
 *
 * @since 3.0
 */
public interface IFontProvider {

	/**
	 * Provides a font for the given element.
	 *
	 * @param element the element
	 * @return the font for the element, or <code>null</code>
	 *   to use the default font
	 */
	public Font getFont(Object element);
}
