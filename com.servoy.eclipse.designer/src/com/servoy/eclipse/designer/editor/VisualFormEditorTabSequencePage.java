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
package com.servoy.eclipse.designer.editor;


import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.grouplayout.GroupLayout;
import org.eclipse.swt.layout.grouplayout.LayoutStyle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebObjectSpecification;

import com.servoy.eclipse.designer.editor.commands.RefreshingCommand;
import com.servoy.eclipse.designer.property.SetValueCommand;
import com.servoy.eclipse.dnd.FormElementDragData.PersistDragData;
import com.servoy.eclipse.dnd.FormElementTransfer;
import com.servoy.eclipse.dnd.IDragData;
import com.servoy.eclipse.model.util.ModelUtils;
import com.servoy.eclipse.model.util.WebFormComponentChildType;
import com.servoy.eclipse.ui.property.PersistPropertySource;
import com.servoy.eclipse.ui.views.IndexedListViewer;
import com.servoy.eclipse.ui.views.IndexedStructuredSelection;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IBasicWebComponent;
import com.servoy.j2db.persistence.IBasicWebObject;
import com.servoy.j2db.persistence.IFlattenedPersistWrapper;
import com.servoy.j2db.persistence.IFormElement;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.ISupportDataProviderID;
import com.servoy.j2db.persistence.ISupportTabSeq;
import com.servoy.j2db.persistence.StaticContentSpecLoader;
import com.servoy.j2db.server.ngclient.FormElement;
import com.servoy.j2db.server.ngclient.FormElementHelper;
import com.servoy.j2db.server.ngclient.FormElementHelper.FormComponentCache;
import com.servoy.j2db.server.ngclient.FormElementHelper.TabSeqProperty;
import com.servoy.j2db.server.ngclient.property.types.FormComponentPropertyType;
import com.servoy.j2db.server.ngclient.property.types.NGTabSeqPropertyType;
import com.servoy.j2db.server.ngclient.template.FormTemplateGenerator;
import com.servoy.j2db.util.SortedList;
import com.servoy.j2db.util.Utils;

/**
 * Tab in form editor for managing tab sequences.
 *
 * @author rgansevles
 */

public class VisualFormEditorTabSequencePage extends Composite
{
	public static final ILabelProvider LABELPROVIDER = new LabelProvider();
	public static final IContentProvider CONTENTPROVIDER = new ArrayContentProvider();

	private final BaseVisualFormEditor editor;
	private IndexedListViewer availableListViewer;
	private TableViewer selectedTableViewer;
	private Button upButton;
	private Button removeButton;
	private Button addButton;
	private Button downButton;
	private Button defaultButton;
	private boolean initialised = false;
	private boolean doRefresh;


	public VisualFormEditorTabSequencePage(BaseVisualFormEditor editor, Composite parent, int style)
	{
		super(parent, style);
		this.editor = editor;
		createContents();
		initialised = true;
		doRefresh();
	}

	protected void createContents()
	{
		// Scrolling container
		this.setLayout(new FillLayout());
		ScrolledComposite scrolledComposite = new ScrolledComposite(this, SWT.H_SCROLL | SWT.V_SCROLL);
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);

		Composite container = new Composite(scrolledComposite, SWT.NONE);
		scrolledComposite.setContent(container);


		removeButton = new Button(container, SWT.PUSH);
		removeButton.setText("<<");
		removeButton.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				handleRemoveElements();
			}
		});

		addButton = new Button(container, SWT.PUSH);
		addButton.setText(">>");
		addButton.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				handleAddElements();
			}
		});

		upButton = new Button(container, SWT.PUSH);
		upButton.setText("up");
		upButton.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				handleMoveElements(-1);
			}
		});

		downButton = new Button(container, SWT.PUSH);
		downButton.setText("down");
		downButton.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				handleMoveElements(1);
			}
		});

		defaultButton = new Button(container, SWT.PUSH);
		defaultButton.setText("Default");
		defaultButton.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				executeCommand(getSetDefaultCommand());
			}
		});

		availableListViewer = new IndexedListViewer(container, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		availableListViewer.addOpenListener(new IOpenListener()
		{
			public void open(OpenEvent event)
			{
				handleAddElements();
			}
		});
		availableListViewer.addSelectionChangedListener(new ISelectionChangedListener()
		{
			public void selectionChanged(SelectionChangedEvent event)
			{
				configureButtons();
			}
		});
		availableListViewer.setLabelProvider(LABELPROVIDER);
		availableListViewer.setContentProvider(CONTENTPROVIDER);
		org.eclipse.swt.widgets.List availableList = availableListViewer.getList();

		selectedTableViewer = new TableViewer(container, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		selectedTableViewer.addOpenListener(new IOpenListener()
		{
			public void open(OpenEvent event)
			{
				handleRemoveElements();
			}
		});
		selectedTableViewer.addSelectionChangedListener(new ISelectionChangedListener()
		{
			public void selectionChanged(SelectionChangedEvent event)
			{
				configureButtons();
			}
		});
		selectedTableViewer.setLabelProvider(LABELPROVIDER);
		selectedTableViewer.setContentProvider(CONTENTPROVIDER);
		org.eclipse.swt.widgets.Table selectedTable = selectedTableViewer.getTable();

		Transfer[] types = new Transfer[] { FormElementTransfer.getInstance() };
		DragSource source = new DragSource(selectedTable, DND.DROP_MOVE | DND.DROP_COPY);
		source.setTransfer(types);

		source.addDragListener(new DragSourceAdapter()
		{
			@Override
			public void dragSetData(DragSourceEvent event)
			{
				// Get the selected items in the drag source
				DragSource ds = (DragSource)event.widget;
				Table table = (Table)ds.getControl();
				TableItem[] selection = table.getSelection();
				if (selection != null && selection.length > 0)
					event.data = new Object[] { Platform.getAdapterManager().getAdapter(((TabSeqProperty)selection[0].getData()).element, IDragData.class) };
			}
		});

		// Create the drop target
		DropTarget target = new DropTarget(selectedTable, DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_DEFAULT);
		target.setTransfer(types);
		target.addDropListener(new DropTargetAdapter()
		{
			@Override
			public void dragEnter(DropTargetEvent event)
			{
				if (event.detail == DND.DROP_DEFAULT)
				{
					event.detail = (event.operations & DND.DROP_COPY) != 0 ? DND.DROP_COPY : DND.DROP_NONE;
				}

				for (TransferData dataType : event.dataTypes)
				{
					if (FormElementTransfer.getInstance().isSupportedType(dataType))
					{
						event.currentDataType = dataType;
					}
				}
			}

			@Override
			public void dragOver(DropTargetEvent event)
			{
				event.feedback = DND.FEEDBACK_SELECT | DND.FEEDBACK_SCROLL;
			}

			@Override
			public void drop(DropTargetEvent event)
			{
				if (FormElementTransfer.getInstance().isSupportedType(event.currentDataType))
				{
					// Get the dropped data
					DropTarget dropTarget = (DropTarget)event.widget;
					Table table = (Table)dropTarget.getControl();
					Object data = event.data;
					if (data instanceof Object[] && ((Object[])data).length == 1)
					{
						Object dragSource = ((Object[])data)[0];
						if (dragSource instanceof PersistDragData && event.item instanceof TableItem)
						{
							int targetIndex = Arrays.asList(table.getItems()).indexOf(event.item);
							List<TabSeqProperty> input = (List<TabSeqProperty>)selectedTableViewer.getInput();
							int sourceIndex = -1;
							for (TabSeqProperty persistProperty : input)
							{
								if (persistProperty.element.getUUID().equals(((PersistDragData)dragSource).uuid))
								{
									sourceIndex = input.indexOf(persistProperty);
									break;
								}
							}
							if (sourceIndex >= 0 && targetIndex >= 0 && sourceIndex != targetIndex)
							{
								handleMoveElements(targetIndex - sourceIndex);
							}
						}
					}
					table.redraw();
				}
			}
		});
		Label availableElementsLabel;
		availableElementsLabel = new Label(container, SWT.NONE);
		availableElementsLabel.setText("Available elements");

		Label selectedElementsLabel;
		selectedElementsLabel = new Label(container, SWT.NONE);
		selectedElementsLabel.setText("Selected elements");
		final GroupLayout groupLayout = new GroupLayout(container);
		groupLayout.setHorizontalGroup(groupLayout.createParallelGroup(GroupLayout.LEADING).add(
			groupLayout.createSequentialGroup().addContainerGap().add(groupLayout.createParallelGroup(GroupLayout.LEADING).add(
				groupLayout.createSequentialGroup().add(groupLayout.createParallelGroup(GroupLayout.LEADING).add(
					groupLayout.createSequentialGroup().add(availableList, GroupLayout.PREFERRED_SIZE, 175, Short.MAX_VALUE).addPreferredGap(
						LayoutStyle.RELATED).add(
							groupLayout.createParallelGroup(GroupLayout.LEADING).add(addButton, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE).add(
								removeButton, GroupLayout.PREFERRED_SIZE, 60, GroupLayout.PREFERRED_SIZE)))
					.add(availableElementsLabel)).addPreferredGap(
						LayoutStyle.RELATED)
					.add(
						groupLayout.createParallelGroup(GroupLayout.LEADING).add(selectedTable, GroupLayout.PREFERRED_SIZE, 175,
							Short.MAX_VALUE).add(selectedElementsLabel, GroupLayout.PREFERRED_SIZE, 134,
								GroupLayout.PREFERRED_SIZE))
					.addPreferredGap(LayoutStyle.RELATED).add(
						groupLayout.createParallelGroup(GroupLayout.TRAILING).add(upButton, GroupLayout.PREFERRED_SIZE, 60,
							GroupLayout.PREFERRED_SIZE).add(downButton, GroupLayout.PREFERRED_SIZE, 60,
								GroupLayout.PREFERRED_SIZE)))
				.add(defaultButton, GroupLayout.PREFERRED_SIZE, 81,
					GroupLayout.PREFERRED_SIZE))
				.addContainerGap()));
		groupLayout.setVerticalGroup(groupLayout.createParallelGroup(GroupLayout.LEADING).add(groupLayout.createSequentialGroup().addContainerGap().add(
			groupLayout.createParallelGroup(GroupLayout.TRAILING).add(availableElementsLabel).add(selectedElementsLabel)).addPreferredGap(
				LayoutStyle.RELATED)
			.add(
				groupLayout.createParallelGroup(GroupLayout.LEADING).add(
					groupLayout.createSequentialGroup().add(upButton).addPreferredGap(LayoutStyle.RELATED).add(downButton)).add(
						groupLayout.createParallelGroup(GroupLayout.LEADING).add(selectedTable, GroupLayout.PREFERRED_SIZE, 188, Short.MAX_VALUE).add(
							groupLayout.createSequentialGroup().addPreferredGap(LayoutStyle.RELATED).add(
								groupLayout.createParallelGroup(GroupLayout.LEADING).add(
									groupLayout.createSequentialGroup().add(removeButton).addPreferredGap(LayoutStyle.RELATED).add(addButton)).add(
										availableList, GroupLayout.PREFERRED_SIZE, 188, Short.MAX_VALUE)))))
			.add(10, 10, 10).add(
				defaultButton)
			.addContainerGap()));
		container.setLayout(groupLayout);

		scrolledComposite.setMinSize(container.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}


	public void refresh()
	{
		if (!initialised || isDisposed() || editor.getForm() == null) return;

		if (isVisible())
		{
			doRefresh();
		}
		else
		{
			doRefresh = true;
		}
	}

	public void doRefresh()
	{
		// preserve selection when possible
		final ISelection availableSelection = availableListViewer.getSelection();
		final ISelection selectedSelection = selectedTableViewer.getSelection();
		doRefresh = false;

		Job.create("refresh tabsequence", (IProgressMonitor monitor) -> {
			SortedList<TabSeqProperty> available = new SortedList<TabSeqProperty>(new Comparator<TabSeqProperty>()
			{
				public int compare(TabSeqProperty o1, TabSeqProperty o2)
				{
					String name1 = "";
					String name2 = "";
					IFormElement el1 = o1.element instanceof WebFormComponentChildType ? ((WebFormComponentChildType)o1.element).getElement()
						: (IFormElement)o1.element;
					IFormElement el2 = o2.element instanceof WebFormComponentChildType ? ((WebFormComponentChildType)o2.element).getElement()
						: (IFormElement)o2.element;
					if (el1.getName() != null)
					{
						name1 += el1.getName();
					}
					if (el2.getName() != null)
					{
						name2 += el2.getName();
					}
					if (el1 instanceof ISupportDataProviderID)
					{
						name1 += ((ISupportDataProviderID)el1).getDataProviderID();
					}
					if (el2 instanceof ISupportDataProviderID)
					{
						name2 += ((ISupportDataProviderID)el2).getDataProviderID();
					}
					return name1.compareTo(name2);
				}
			});
			SortedList<TabSeqProperty> selected = new SortedList<TabSeqProperty>(new Comparator<TabSeqProperty>()
			{
				public int compare(TabSeqProperty o1, TabSeqProperty o2)
				{
					return FormElementHelper.compareTabSeq(o1.getSeqValue(), o1.element, o2.getSeqValue(), o2.element,
						ModelUtils.getEditingFlattenedSolution(editor.getForm()));
				}
			});
			List<IFormElement> elements = ModelUtils.getEditingFlattenedSolution(editor.getForm()).getFlattenedForm(editor.getForm()).getFlattenedObjects(null);
			for (IFormElement persist : elements)
			{
				if (FormTemplateGenerator.isWebcomponentBean(persist))
				{
					IBasicWebComponent webComponent = (IBasicWebComponent)persist;
					String componentType = FormTemplateGenerator.getComponentTypeName(webComponent);
					WebObjectSpecification specification = WebComponentSpecProvider.getSpecProviderState().getWebComponentSpecification(componentType);
					if (specification != null)
					{
						Collection<PropertyDescription> properties = specification.getProperties(NGTabSeqPropertyType.NG_INSTANCE);
						if (properties != null && properties.size() > 0)
						{
							for (PropertyDescription pd : properties)
							{
								int tabseq = Utils.getAsInteger(webComponent.getProperty(pd.getName()));
								if (tabseq >= 0)
								{
									selected.add(new TabSeqProperty(persist, pd.getName()));
								}
								else
								{
									available.add(new TabSeqProperty(persist, pd.getName()));
								}
							}
						}
						properties = specification.getProperties(FormComponentPropertyType.INSTANCE);
						addFormComponentProperties(persist, properties, available, selected, (IBasicWebComponent)persist);
					}
				}
				else if (persist instanceof ISupportTabSeq)
				{
					if (((ISupportTabSeq)persist).getTabSeq() >= 0)
					{
						selected.add(new TabSeqProperty(persist, null));
					}
					else
					{
						available.add(new TabSeqProperty(persist, null));
					}
				}

			}

			Display.getDefault().asyncExec(() -> {
				availableListViewer.setInput(available);
				selectedTableViewer.setInput(selected);

				availableListViewer.setSelection(availableSelection);
				selectedTableViewer.setSelection(selectedSelection);

				configureButtons();
			});
		}).schedule();

	}

	private void addFormComponentProperties(IFormElement persist, Collection<PropertyDescription> properties, List<TabSeqProperty> available,
		List<TabSeqProperty> selected, IBasicWebObject parentComponent)
	{
		if (properties.size() > 0 && !FormElementHelper.isListFormComponent(properties))
		{
			FlattenedSolution fs = ModelUtils.getEditingFlattenedSolution(editor.getForm());
			FormElement formComponentEl = FormElementHelper.INSTANCE.getFormElement(persist, fs, null, true);
			for (PropertyDescription pd : properties)
			{
				Object propertyValue = formComponentEl.getPropertyValue(pd.getName());
				Form frm = FormComponentPropertyType.INSTANCE.getForm(propertyValue, fs);
				if (frm == null) continue;
				FormComponentCache cache = FormElementHelper.INSTANCE.getFormComponentCache(formComponentEl, pd, (JSONObject)propertyValue, frm, fs);
				for (FormElement element : cache.getFormComponentElements())
				{
					IPersist p = element.getPersistIfAvailable();
					if (p instanceof IFormElement)
					{
						String[] name = ((IFormElement)p).getName().split("\\$");
						if (name.length > 2 && !name[name.length - 1].startsWith(FormElement.SVY_NAME_PREFIX))
						{
							StringBuilder keyBuilder = new StringBuilder();
							for (int i = 1; i < name.length; i++)
							{
								if (i > 1) keyBuilder.append(".");
								keyBuilder.append(name[i]);
							}
							WebFormComponentChildType formComponentChild = new WebFormComponentChildType(parentComponent, keyBuilder.toString(), fs);

							if (p instanceof ISupportTabSeq)
							{
								if (((ISupportTabSeq)p).getTabSeq() >= 0)
								{
									selected.add(new TabSeqProperty(formComponentChild, "tabSeq"));
								}
								else
								{
									available.add(new TabSeqProperty(formComponentChild, "tabSeq"));
								}
							}
							else if (FormTemplateGenerator.isWebcomponentBean(p))
							{
								IBasicWebComponent innerWebComponent = (IBasicWebComponent)p;
								String innerComponentType = FormTemplateGenerator.getComponentTypeName(innerWebComponent);
								WebObjectSpecification innerSpecification = WebComponentSpecProvider.getSpecProviderState().getWebComponentSpecification(
									innerComponentType);
								if (innerSpecification != null)
								{
									Collection<PropertyDescription> innerTabSeqproperties = innerSpecification.getProperties(NGTabSeqPropertyType.NG_INSTANCE);
									if (innerTabSeqproperties != null && innerTabSeqproperties.size() > 0)
									{
										for (PropertyDescription tabSeqPD : innerTabSeqproperties)
										{
											int tabseq = Utils.getAsInteger(formComponentChild.getProperty(tabSeqPD.getName()));
											if (tabseq >= 0)
											{
												selected.add(new TabSeqProperty(formComponentChild, tabSeqPD.getName()));
											}
											else
											{
												available.add(new TabSeqProperty(formComponentChild, tabSeqPD.getName()));
											}
										}
									}
								}
								Collection<PropertyDescription> nestedProperties = innerSpecification.getProperties(FormComponentPropertyType.INSTANCE);
								addFormComponentProperties(innerWebComponent, nestedProperties, available, selected, parentComponent);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public void setVisible(boolean visible)
	{
		super.setVisible(visible);
		if (visible && doRefresh)
		{
			doRefresh();
		}
	}

	protected void configureButtons()
	{
		if (!initialised) return;

		StructuredSelection selection = (StructuredSelection)selectedTableViewer.getSelection();
		boolean enableUp = false;
		boolean enableDown = false;
		if (selection.size() == 1)
		{
			int selectedIndex = ((List)selectedTableViewer.getInput()).indexOf(selection.getFirstElement());
			int nSelected = ((List)selectedTableViewer.getInput()).size();
			enableUp = selectedIndex > 0;
			enableDown = selectedIndex < nSelected - 1;
		}
		upButton.setEnabled(enableUp);
		downButton.setEnabled(enableDown);

		removeButton.setEnabled(selection.size() > 0);
		addButton.setEnabled(((IStructuredSelection)availableListViewer.getSelection()).size() > 0);
		defaultButton.setEnabled(getSetDefaultCommand() != null);
	}

	/**
	 * Create a command for saving the tab sequences, return null if there is nothing to save.
	 *
	 * @param tabSeqs
	 * @param label
	 * @return
	 */
	protected Command getSaveCommand(int[] tabSeqs, String label)
	{
		CompoundCommand command = null;
		List<TabSeqProperty> available = (List<TabSeqProperty>)availableListViewer.getInput();
		List<TabSeqProperty> selected = (List<TabSeqProperty>)selectedTableViewer.getInput();
		int nAvailable = available.size();
		int nSelected = selected.size();

		for (int i = 0; i < tabSeqs.length; i++)
		{
			TabSeqProperty ts = null;
			if (i < nAvailable)
			{
				ts = available.get(i);
			}
			else if ((i - nAvailable) < nSelected)
			{
				ts = selected.get(i - nAvailable);
			}

			if (ts.getSeqValue() != tabSeqs[i])
			{
				if (command == null)
				{
					command = new CompoundCommand(label);
				}
				command.add(getSetTabSeqCommand(ts, tabSeqs[i]));
			}
		}

		return command;
	}

	protected Command getSetTabSeqCommand(TabSeqProperty tabSeqProperty, int tabSeq)
	{
		IPersist persist = tabSeqProperty.element;
		if (persist instanceof IFlattenedPersistWrapper) persist = ((IFlattenedPersistWrapper< ? >)persist).getWrappedPersist();
		return SetValueCommand.createSetvalueCommand("", PersistPropertySource.createPersistPropertySource(persist, editor.getForm(), false),
			tabSeqProperty.propertyName != null ? tabSeqProperty.propertyName : StaticContentSpecLoader.PROPERTY_TABSEQ.getPropertyName(), new Integer(tabSeq));
	}

	/**
	 * Execute a command on the command stack and refresh the views.
	 *
	 * @param command
	 */
	protected void executeCommand(Command command)
	{
		if (command == null)
		{
			return;
		}
		editor.getCommandStack().execute(command);
	}

	protected void handleAddElements()
	{
		final IndexedStructuredSelection selection = ((IndexedStructuredSelection)availableListViewer.getSelection());

		int[] tabSeqs = getBaseTabIndexes();
		int max = 0;
		for (int element : tabSeqs)
			if (element > max) max = element;
		for (int index : selection.getSelectedIndices())
			tabSeqs[index] = ++max;

		executeCommand(new RefreshingCommand(getSaveCommand(tabSeqs, "add to tab sequence"))
		{
			@Override
			public void refresh(boolean haveExecuted)
			{
				if (haveExecuted)
				{
					selectedTableViewer.setSelection(selection);
				}
				else
				{
					// undo
					availableListViewer.setSelection(selection);
				}
			}
		});
	}

	protected void handleRemoveElements()
	{
		final StructuredSelection selection = ((StructuredSelection)selectedTableViewer.getSelection());
		int nAvailable = ((List)availableListViewer.getInput()).size();

		int[] tabSeqs = getBaseTabIndexes();
		Iterator it = selection.iterator();
		List input = (List)selectedTableViewer.getInput();
		while (it.hasNext())
		{
			int index = input.indexOf(it.next());
			tabSeqs[nAvailable + index] = ISupportTabSeq.SKIP;
		}
		int count = 1;
		for (int i = 0; i < tabSeqs.length; i++)
			if (tabSeqs[i] >= 0) tabSeqs[i] = count++;

		executeCommand(new RefreshingCommand(getSaveCommand(tabSeqs, "remove from tab sequence"))
		{
			@Override
			public void refresh(boolean haveExecuted)
			{
				if (haveExecuted)
				{
					availableListViewer.setSelection(selection);
				}
				else
				{
					// undo
					selectedTableViewer.setSelection(selection);
				}
			}
		});
	}

	protected void handleMoveElements(int moveIndex)
	{
		final StructuredSelection selection = ((StructuredSelection)selectedTableViewer.getSelection());
		if (selection.size() == 1)
		{
			int nAvailable = ((List)availableListViewer.getInput()).size();
			int nSelected = ((List)selectedTableViewer.getInput()).size();

			int selectedIndex = ((List)selectedTableViewer.getInput()).indexOf(selection.getFirstElement());
			int[] tabSeqs = getBaseTabIndexes();

			// flip the selected with its sibling
			if (moveIndex < 0 && (selectedIndex + moveIndex) >= 0)
			{
				for (int j = moveIndex; j < 0; j++)
				{
					tabSeqs[nAvailable + selectedIndex + j] = selectedIndex + j + 2;
				}
				tabSeqs[nAvailable + selectedIndex] = selectedIndex + moveIndex + 1;
			}
			else if (moveIndex > 0 && (selectedIndex + moveIndex <= nSelected - 1))
			{
				for (int j = 1; j <= moveIndex; j++)
				{
					tabSeqs[nAvailable + selectedIndex + j] = selectedIndex + j;
				}
				tabSeqs[nAvailable + selectedIndex] = selectedIndex + moveIndex + 1;
			}

			executeCommand(new RefreshingCommand(getSaveCommand(tabSeqs, "change tab index"))
			{
				@Override
				public void refresh(boolean haveExecuted)
				{
					selectedTableViewer.setSelection(selection);
				}
			});
		}
	}

	private int[] getBaseTabIndexes()
	{
		int nAvailable = ((List)availableListViewer.getInput()).size();
		int nSelected = ((List)selectedTableViewer.getInput()).size();

		int[] tabSeqs = new int[nAvailable + nSelected];

		// first the non-selected
		for (int i = 0; i < nAvailable; i++)
		{
			tabSeqs[i] = ISupportTabSeq.SKIP;
		}

		// then the selected
		for (int i = 0; i < nSelected; i++)
		{
			tabSeqs[nAvailable + i] = i + 1;
		}

		return tabSeqs;
	}

	protected Command getSetDefaultCommand()
	{
		int nFields = ((List)availableListViewer.getInput()).size() + ((List)selectedTableViewer.getInput()).size();
		int[] tabSeqs = new int[nFields];
		for (int i = 0; i < nFields; i++)
		{
			tabSeqs[i] = ISupportTabSeq.DEFAULT;
		}
		return getSaveCommand(tabSeqs, "set default tab sequence");
	}
}
