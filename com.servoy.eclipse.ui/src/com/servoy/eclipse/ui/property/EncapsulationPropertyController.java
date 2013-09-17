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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;

import com.servoy.eclipse.ui.Messages;
import com.servoy.eclipse.ui.property.ComplexProperty.ComplexPropertyConverter;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.PersistEncapsulation;
import com.servoy.j2db.persistence.Relation;

/**
 * Property controller for encapsulation properties.
 * 
 * @author jcompagner
 * @since 6.0
 */
public class EncapsulationPropertyController extends PropertyController<Integer, Object>
{
	private final IPersist persist;

	public EncapsulationPropertyController(String id, String displayName, IPersist persist)
	{
		super(id, displayName, null, EncapsulationLabelProvider.LABEL_INSTANCE, new DummyCellEditorFactory(EncapsulationLabelProvider.LABEL_INSTANCE));
		this.persist = persist;
	}

	@Override
	protected IPropertyConverter<Integer, Object> createConverter()
	{
		return new EncapsulationPropertyConverter();
	}

	public class EncapsulationPropertyConverter extends ComplexPropertyConverter<Integer>
	{
		@Override
		public Object convertProperty(Object id, Integer value)
		{
			return new ComplexProperty<Integer>(new Integer(0).equals(value) ? new Integer(PersistEncapsulation.DEFAULT) : value)
			{
				@Override
				public IPropertySource getPropertySource()
				{
					EncapsulationPropertySource encapsulationPropertySource = new EncapsulationPropertySource(this, persist);
					encapsulationPropertySource.setReadonly(EncapsulationPropertyController.this.isReadOnly());
					return encapsulationPropertySource;
				}
			};
		}
	}

	@SuppressWarnings("nls")
	public static class EncapsulationPropertySource extends ComplexPropertySource<Integer>
	{
		private static final int ALL = PersistEncapsulation.PRIVATE + PersistEncapsulation.MODULE_PRIVATE + PersistEncapsulation.HIDE_CONTROLLER +
			PersistEncapsulation.HIDE_DATAPROVIDERS + PersistEncapsulation.HIDE_ELEMENTS + PersistEncapsulation.HIDE_FOUNDSET;

		private static final String PRIVATE = "private";
		private static final String MODULE_PRIVATE = "module private";
		private static final String HIDE_DATAPROVIDERS = "hide dataproviders";
		private static final String HIDE_FOUNDSET = "hide foundset";
		private static final String HIDE_CONTROLLER = "hide controller";
		private static final String HIDE_ELEMENTS = "hide elements";

		private final IPersist persist;

		public EncapsulationPropertySource(ComplexProperty<Integer> complexProperty, IPersist persist)
		{
			super(complexProperty);
			this.persist = persist;
		}

		@Override
		public Object resetComplexPropertyValue(Object id)
		{
			return Integer.valueOf(PersistEncapsulation.DEFAULT);
		}

		@Override
		public IPropertyDescriptor[] createPropertyDescriptors()
		{
			List<IPropertyDescriptor> propertyDescriptors = new ArrayList<IPropertyDescriptor>();
			if (persist instanceof Form || persist instanceof Relation)
			{
				propertyDescriptors.add(new CheckboxPropertyDescriptor(PRIVATE,
					EncapsulationLabelProvider.LABEL_INSTANCE.getText(Integer.valueOf(PersistEncapsulation.PRIVATE))));
			}
			propertyDescriptors.add(new CheckboxPropertyDescriptor(MODULE_PRIVATE,
				EncapsulationLabelProvider.LABEL_INSTANCE.getText(Integer.valueOf(PersistEncapsulation.MODULE_PRIVATE))));
			if (persist instanceof Form)
			{
				propertyDescriptors.add(new CheckboxPropertyDescriptor(HIDE_DATAPROVIDERS,
					EncapsulationLabelProvider.LABEL_INSTANCE.getText(Integer.valueOf(PersistEncapsulation.HIDE_DATAPROVIDERS))));
				propertyDescriptors.add(new CheckboxPropertyDescriptor(HIDE_FOUNDSET,
					EncapsulationLabelProvider.LABEL_INSTANCE.getText(Integer.valueOf(PersistEncapsulation.HIDE_FOUNDSET))));
				propertyDescriptors.add(new CheckboxPropertyDescriptor(HIDE_CONTROLLER,
					EncapsulationLabelProvider.LABEL_INSTANCE.getText(Integer.valueOf(PersistEncapsulation.HIDE_CONTROLLER))));
				propertyDescriptors.add(new CheckboxPropertyDescriptor(HIDE_ELEMENTS,
					EncapsulationLabelProvider.LABEL_INSTANCE.getText(Integer.valueOf(PersistEncapsulation.HIDE_ELEMENTS))));
			}
			return propertyDescriptors.toArray(new IPropertyDescriptor[0]);
		}

		@Override
		public Object getPropertyValue(Object id)
		{
			int encapsulation = getEditableValue().intValue();
			if (encapsulation == -1) return Boolean.FALSE;

			if (PRIVATE.equals(id)) return (encapsulation & PersistEncapsulation.PRIVATE) == 0 ? Boolean.FALSE : Boolean.TRUE;
			if (MODULE_PRIVATE.equals(id)) return (encapsulation & PersistEncapsulation.MODULE_PRIVATE) == 0 ? Boolean.FALSE : Boolean.TRUE;
			if (HIDE_DATAPROVIDERS.equals(id)) return (encapsulation & PersistEncapsulation.HIDE_DATAPROVIDERS) == 0 ? Boolean.FALSE : Boolean.TRUE;
			if (HIDE_FOUNDSET.equals(id)) return (encapsulation & PersistEncapsulation.HIDE_FOUNDSET) == 0 ? Boolean.FALSE : Boolean.TRUE;
			if (HIDE_CONTROLLER.equals(id)) return (encapsulation & PersistEncapsulation.HIDE_CONTROLLER) == 0 ? Boolean.FALSE : Boolean.TRUE;
			if (HIDE_ELEMENTS.equals(id)) return (encapsulation & PersistEncapsulation.HIDE_ELEMENTS) == 0 ? Boolean.FALSE : Boolean.TRUE;
			return null;
		}

		@Override
		public Integer setComplexPropertyValue(Object id, Object v)
		{
			int encapsulation = getEditableValue().intValue();
			if (encapsulation == -1)
			{
				encapsulation = 0;
			}
			int flag;
			if (PRIVATE.equals(id)) flag = PersistEncapsulation.PRIVATE;
			else if (MODULE_PRIVATE.equals(id)) flag = PersistEncapsulation.MODULE_PRIVATE;
			else if (HIDE_DATAPROVIDERS.equals(id)) flag = PersistEncapsulation.HIDE_DATAPROVIDERS;
			else if (HIDE_FOUNDSET.equals(id)) flag = PersistEncapsulation.HIDE_FOUNDSET;
			else if (HIDE_CONTROLLER.equals(id)) flag = PersistEncapsulation.HIDE_CONTROLLER;
			else if (HIDE_ELEMENTS.equals(id)) flag = PersistEncapsulation.HIDE_ELEMENTS;
			else return null;

			if (Boolean.TRUE.equals(v))
			{
				encapsulation |= flag;
			}
			else
			{
				encapsulation &= (ALL - flag);
			}

			return new Integer(encapsulation);
		}
	}


	public static class EncapsulationLabelProvider extends LabelProvider
	{
		public static EncapsulationLabelProvider LABEL_INSTANCE = new EncapsulationLabelProvider();

		@Override
		public String getText(Object element)
		{
			int encapsulation = ((Integer)element).intValue();

			if (encapsulation == 0)
			{
				return Messages.Public;
			}

			StringBuilder retval = new StringBuilder();
			if ((encapsulation & PersistEncapsulation.PRIVATE) == PersistEncapsulation.PRIVATE)
			{
				retval.append(Messages.Private);
			}
			if ((encapsulation & PersistEncapsulation.MODULE_PRIVATE) == PersistEncapsulation.MODULE_PRIVATE)
			{
				if (retval.length() != 0) retval.append(',');
				retval.append(Messages.ModulePrivate);
			}
			if ((encapsulation & PersistEncapsulation.HIDE_CONTROLLER) == PersistEncapsulation.HIDE_CONTROLLER)
			{
				if (retval.length() != 0) retval.append(',');
				retval.append(Messages.HideController);
			}
			if ((encapsulation & PersistEncapsulation.HIDE_DATAPROVIDERS) == PersistEncapsulation.HIDE_DATAPROVIDERS)
			{
				if (retval.length() != 0) retval.append(',');
				retval.append(Messages.HideDataproviders);
			}
			if ((encapsulation & PersistEncapsulation.HIDE_FOUNDSET) == PersistEncapsulation.HIDE_FOUNDSET)
			{
				if (retval.length() != 0) retval.append(',');
				retval.append(Messages.HideFoundset);
			}
			if ((encapsulation & PersistEncapsulation.HIDE_ELEMENTS) == PersistEncapsulation.HIDE_ELEMENTS)
			{
				if (retval.length() != 0) retval.append(',');
				retval.append(Messages.HideElements);
			}
			return retval.toString();
		}
	}
}
