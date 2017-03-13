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

package com.servoy.eclipse.model.export;

import org.json.JSONObject;

/**
 * @author Johan
 *
 */
public interface IExportSolutionModel
{

	public abstract String getFileName();

	public abstract boolean isProtectWithPassword();

	public abstract boolean isExportReferencedModules();

	public abstract boolean isExportMetaData();

	public abstract boolean isExportSampleData();

	public abstract boolean isExportI18NData();

	public abstract boolean isExportUsers();

	public abstract String[] getModulesToExport();

	public abstract String getPassword();

	public abstract int getNumberOfSampleDataExported();

	public abstract boolean isExportAllTablesFromReferencedServers();

	public abstract boolean isCheckMetadataTables();

	public abstract boolean isExportUsingDbiFileInfoOnly();

	public abstract boolean useImportSettings();

	public abstract JSONObject getImportSettings();

}