/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2020 Servoy BV

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

package com.servoy.eclipse.ui.views;

import java.awt.Dimension;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;

import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.Util;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.json.JSONArray;
import org.json.JSONObject;

import com.servoy.eclipse.ui.dialogs.BrowserDialog;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Utils;

/**
 * @author lvostinar
 *
 */
public class TutorialView extends ViewPart
{
	public static final String PART_ID = "com.servoy.eclipse.ui.views.TutorialView";
	private static boolean isTutorialViewOpen = false;
	private static final String URL_TUTORIALS_LIST = "https://tutorials.servoy.com/servoy-service/rest_ws/contentAPI/listtutorials";

	private JSONObject dataModel;
	private JSONArray dataTutorialsList;
	BrowserDialog dialog = null;

	private Composite rootComposite;

	@Override
	public void createPartControl(Composite parent)
	{
		loadDataModel(true, null);
		createTutorialView(parent, true);
	}

	private void createTutorialView(Composite parent, boolean createDefaultTutorialsList)
	{
		rootComposite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.verticalSpacing = 0;
		rootComposite.setLayout(layout);
		rootComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		rootComposite.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
		ScrolledComposite sc = new ScrolledComposite(rootComposite, SWT.H_SCROLL | SWT.V_SCROLL);
		sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		Composite mainContainer = new Composite(sc, SWT.NONE);
		sc.setContent(mainContainer);
		mainContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		mainContainer.setLayout(layout);
		mainContainer.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

		if (createDefaultTutorialsList)
		{
			new TutorialsList(mainContainer);
		}
		else
		{
			Label nameLabel = new Label(mainContainer, SWT.NONE);
			nameLabel.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
			nameLabel.setText(dataModel.optString("name"));
			FontDescriptor descriptor = FontDescriptor.createFrom(nameLabel.getFont());
			descriptor = descriptor.setStyle(SWT.BOLD);
			descriptor = descriptor.increaseHeight(12);
			nameLabel.setFont(descriptor.createFont(this.getViewSite().getShell().getDisplay()));

			JSONArray steps = dataModel.optJSONArray("steps");
			if (steps != null)
			{
				for (int i = 0; i < steps.length(); i++)
				{
					new RowComposite(mainContainer, steps.getJSONObject(i), i + 1);
				}
			}

			new TutorialButtons(mainContainer);
		}
		sc.setExpandHorizontal(true);
		sc.setExpandVertical(true);
		sc.setMinSize(mainContainer.computeSize(SWT.DEFAULT, SWT.DEFAULT, true));
		mainContainer.layout();
		sc.layout();
	}

	/**
	 * This class is representing the default tutorials list.
	 */
	private class TutorialsList extends Composite
	{
		public TutorialsList(Composite parent)
		{
			super(parent, SWT.FILL);
			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			layout.verticalSpacing = 1;
			layout.marginHeight = 1;
			setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			setLayout(layout);
			setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

			Composite row = new Composite(this, SWT.FILL);
			row.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			row.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

			GridLayout rowLayout = new GridLayout();
			rowLayout.numColumns = 1;
			rowLayout.horizontalSpacing = 10;
			rowLayout.marginHeight = 1;
			rowLayout.marginTop = 10;
			row.setLayout(rowLayout);

			if (dataTutorialsList != null)
			{
				for (int i = 0; i < dataTutorialsList.length(); i++)
				{
					Label name = new Label(row, SWT.WRAP);
					name.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
					name.setText(dataTutorialsList.getJSONObject(i).optString("title"));
					name.setCursor(new Cursor(parent.getDisplay(), SWT.CURSOR_HAND));
					FontDescriptor descriptor = FontDescriptor.createFrom(name.getFont());
					descriptor = descriptor.setStyle(SWT.BOLD);
					descriptor = descriptor.increaseHeight(6);
					name.setFont(descriptor.createFont(parent.getDisplay()));
					final String tutorialID = dataTutorialsList.getJSONObject(i).optString("id");

					Label description = new Label(row, SWT.WRAP);
					description.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
					description.setText(removeHTMLTags(dataTutorialsList.getJSONObject(i).optString("description")));
					GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
					gd.widthHint = 200;
					description.setLayoutData(gd);
					descriptor = FontDescriptor.createFrom(description.getFont());
					descriptor = descriptor.increaseHeight(2);
					description.setFont(descriptor.createFont(parent.getDisplay()));

					StyledText openLink = new StyledText(row, SWT.NONE);
					openLink.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
					openLink.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
					openLink.setEditable(false);
					openLink.setText("open");
					openLink.setCursor(new Cursor(parent.getDisplay(), SWT.CURSOR_HAND));
					descriptor = FontDescriptor.createFrom(openLink.getFont());
					openLink.setFont(descriptor.createFont(parent.getDisplay()));
					openLink.addMouseListener(new MouseAdapter()
					{
						@Override
						public void mouseUp(MouseEvent event)
						{
							super.mouseUp(event);

							if (event.getSource() instanceof StyledText)
							{
								BrowserDialog tutorialDialog = new BrowserDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
									"http://tutorials.servoy.com/solutions/content/index.html?viewTutorial=" + tutorialID, true, false);
								tutorialDialog.open(true);
							}
						}
					});
				}
			}
		}
	}

	/**
	 * Buttons used for opening the videos for the current and next tutorial.
	 */
	private class TutorialButtons extends Composite
	{
		public TutorialButtons(Composite parent)
		{
			super(parent, SWT.FILL);
			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			layout.verticalSpacing = 1;
			layout.marginHeight = 1;
			setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			setLayout(layout);
			setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

			Composite row = new Composite(this, SWT.FILL);
			row.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			row.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

			GridLayout rowLayout = new GridLayout();
			rowLayout.numColumns = 2;
			rowLayout.horizontalSpacing = 10;
			rowLayout.marginHeight = 1;
			rowLayout.marginTop = 10;
			row.setLayout(rowLayout);

			Button currentTutorial = new Button(row, SWT.BUTTON1);
			currentTutorial.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
			currentTutorial.setText("Watch again");
			currentTutorial.setToolTipText("Play the video for this tutorial.");
			currentTutorial.setCursor(new Cursor(parent.getDisplay(), SWT.CURSOR_HAND));
			currentTutorial.addListener(SWT.Selection, new Listener()
			{
				@Override
				public void handleEvent(Event event)
				{
					final String currentTutorialID = dataModel.optString("tutorialID");
					BrowserDialog tutorialDialog = new BrowserDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
						"http://tutorials.servoy.com/solutions/content/index.html?viewTutorial=" + currentTutorialID, true, false);
					tutorialDialog.open(true);
				}
			});

			final String nextTutorialID = dataModel.optString("nextTutorialID");
			if (nextTutorialID != null)
			{
				Button nextTutorial = new Button(row, SWT.BUTTON1);
				nextTutorial.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
				nextTutorial.setText("Next tutorial");
				nextTutorial.setToolTipText("Play the next tutorial.");
				GridData gridData = new GridData();
				gridData.horizontalAlignment = GridData.END;
				nextTutorial.setLayoutData(gridData);
				nextTutorial.setCursor(new Cursor(parent.getDisplay(), SWT.CURSOR_HAND));
				nextTutorial.addListener(SWT.Selection, new Listener()
				{
					@Override
					public void handleEvent(Event event)
					{
						BrowserDialog tutorialDialog = new BrowserDialog(PlatformUI.getWorkbench().getDisplay().getActiveShell(),
							"http://tutorials.servoy.com/solutions/content/index.html?viewTutorial=" + nextTutorialID, true, false);
						tutorialDialog.open(true);
					}
				});
			}
		}

	}

	private class RowComposite extends Composite
	{
		RowComposite(Composite parent, JSONObject rowData, int index)
		{
			super(parent, SWT.FILL);
			GridLayout layout = new GridLayout();
			layout.numColumns = 1;
			layout.verticalSpacing = 1;
			layout.marginHeight = 1;
			setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			setLayout(layout);
			setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

			Composite row = new Composite(this, SWT.FILL);
			row.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			row.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

			GridLayout rowLayout = new GridLayout();
			rowLayout.numColumns = 1;
			rowLayout.horizontalSpacing = 10;
			rowLayout.marginHeight = 1;
			rowLayout.marginTop = 10;
			row.setLayout(rowLayout);

			Label stepName = new Label(row, SWT.NONE);
			stepName.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
			stepName.setText(index + ". " + rowData.optString("name"));
			FontDescriptor descriptor = FontDescriptor.createFrom(stepName.getFont());
			descriptor = descriptor.setStyle(SWT.BOLD);
			descriptor = descriptor.increaseHeight(6);
			stepName.setFont(descriptor.createFont(parent.getDisplay()));

			Label stepDescription = new Label(row, SWT.WRAP);
			stepDescription.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
			stepDescription.setText(rowData.optString("description"));
			GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
			gd.widthHint = 200;
			stepDescription.setLayoutData(gd);
			descriptor = FontDescriptor.createFrom(stepDescription.getFont());
			descriptor = descriptor.increaseHeight(2);
			stepDescription.setFont(descriptor.createFont(parent.getDisplay()));


			StyledText gifURL = new StyledText(row, SWT.NONE);
			gifURL.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
			gifURL.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
			gifURL.setEditable(false);
			gifURL.setText("< show");
			StyleRange styleRange = new StyleRange();
			styleRange.underline = true;
			styleRange.start = 0;
			styleRange.length = 6;
			gifURL.setStyleRange(styleRange);
			gifURL.setCursor(new Cursor(parent.getDisplay(), SWT.CURSOR_HAND));
			descriptor = FontDescriptor.createFrom(gifURL.getFont());
			descriptor = descriptor.setHeight(10);
			gifURL.setFont(descriptor.createFont(parent.getDisplay()));
			gifURL.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseUp(MouseEvent e)
				{
					Point cursorLocation = Display.getCurrent().getCursorLocation();
					Dimension size = null;
					try
					{
						ImageDescriptor desc = ImageDescriptor.createFromURL(new URL(rowData.optString("gifURL")));
						ImageData imgData = desc.getImageData(100);
						if (imgData != null)
						{
							if (Util.isMac())
							{
								size = new Dimension(imgData.width, imgData.height);
							}
							else
							{
								Point dpi = Display.getCurrent().getDPI();
								size = new Dimension((int)(imgData.width * (dpi.x / 90f)), (int)(imgData.height * (dpi.y / 90f)));
							}
						}
					}
					catch (MalformedURLException e1)
					{
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					if (size == null)
					{
						size = new Dimension(300, 300);
					}

					Rectangle bounds = Display.getCurrent().getBounds();


					Point location = new Point(cursorLocation.x - size.width - 50, cursorLocation.y + 5);
					if ((location.y + size.height) > bounds.height)
					{
						location.y = bounds.height - size.height - 70;
					}
					if (dialog == null || dialog.isDisposed())
					{
						dialog = new BrowserDialog(parent.getShell(),
							rowData.optString("gifURL"), false, false);
						dialog.open(location, size, false);
					}
					else
					{
						dialog.setUrl(rowData.optString("gifURL"));
						dialog.setLocationAndSize(location, size);
					}
				}
			});
		}
	}

	private void loadDataModel(boolean loadDefaultTutorialList, String url)
	{
		try (InputStream is = new URL(loadDefaultTutorialList ? URL_TUTORIALS_LIST : url).openStream())
		{
			String jsonText = Utils.getTXTFileContent(is, Charset.forName("UTF-8"));
			if (loadDefaultTutorialList)
			{
				dataTutorialsList = new JSONArray(jsonText);
			}
			else
			{
				dataModel = new JSONObject(jsonText);
			}

		}
		catch (Exception e)
		{
			Debug.error(e);
		}
	}

	public void openTutorial(String url)
	{
		loadDataModel(false, url);

		Composite parent = rootComposite.getParent();
		rootComposite.dispose();
		createTutorialView(parent, false);
		parent.layout(true, true);

		isTutorialViewOpen = true;
	}

	public static boolean isTutorialViewOpen()
	{
		return isTutorialViewOpen;
	}

	@Override
	public void dispose()
	{
		isTutorialViewOpen = false;
		super.dispose();
	}

	@Override
	public void setFocus()
	{

	}

	/**
	 * Methods that removes the HTML tags from a text.
	 * @param text the text to be checked.
	 * @return the text without HTML tags.
	 */
	private String removeHTMLTags(final String text)
	{
		String copyText = text;
		while (true)
		{
			if (copyText.indexOf('<') != -1 && copyText.indexOf('>') != -1)
			{
				copyText = copyText.replace(copyText.substring(copyText.indexOf('<'), text.indexOf('>') + 1), "");
			}
			else
			{
				break;
			}
		}
		return copyText;
	}
}
