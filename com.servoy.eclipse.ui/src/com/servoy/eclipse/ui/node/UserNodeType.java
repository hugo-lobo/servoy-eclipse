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
package com.servoy.eclipse.ui.node;

public enum UserNodeType
{
	SERVERS,
	SERVER,
	TABLE,
	TABLES,
	VIEW,
	VIEWS,
	APPLICATION_ITEM,
	HISTORY_ITEM,
	SOLUTION_MODEL_ITEM,
	GLOBAL_VARIABLE_ITEM,
	GLOBAL_METHOD_ITEM,
	FORM_METHOD,
	FORM_CONTROLLER_FUNCTION_ITEM,
	FORM_ELEMENTS_ITEM_METHOD,
	TABLE_COLUMNS_ITEM,
	SECURITY_ITEM,
	SECURITY,
	FUNCTIONS_ITEM,
	PLUGINS_ITEM,
	EXCEPTIONS,
	EXCEPTIONS_ITEM,
	I18N,
	I18N_ITEM,
	CALCULATIONS_ITEM,
	RELEASE,
	SOLUTION,
	ALL_SOLUTIONS,
	SOLUTION_ITEM,
	SOLUTION_ITEM_NOT_ACTIVE_MODULE,
	ALL_WEB_PACKAGES,
	WEB_PACKAGE,
	APPLICATION,
	SOLUTION_MODEL,
	HISTORY,
	GLOBALS_ITEM,
	SCOPES_ITEM,
	SCOPES_ITEM_CALCULATION_MODE,
	GLOBAL_VARIABLES,
	GLOBALSCRIPT,
	GLOBALRELATIONS,
	FOUNDSET,
	FOUNDSET_ITEM,
	FOUNDSET_MANAGER,
	STYLES,
	STYLE_ITEM,
	USER_GROUP_SECURITY,
	I18N_FILES,
	I18N_FILE_ITEM,
	TEMPLATES,
	TEMPLATE_ITEM,
	FOUNDSET_MANAGER_ITEM,
	MODULES,
	MODULE,
	FORMS,
	FORMS_GRAYED_OUT,
	FORM,
	JSLIB,
	DATE,
	ARRAY,
	REGEXP,
	STRING,
	NUMBER,
	UTILS,
	UTIL_ITEM,
	FORM_CONTROLLER,
	FORM_VARIABLES,
	FORM_VARIABLE_ITEM,
	FORM_ELEMENTS,
	FORM_ELEMENTS_INHERITED,
	FORM_ELEMENTS_ITEM,
	FORM_ELEMENTS_GROUP,
	TABLE_COLUMNS,
	FUNCTIONS,
	JSON,
	PLUGINS,
	PLUGIN,
	MIGRATION,
	CALCULATIONS,
	ALL_RELATIONS,
	RELATIONS,
	RELATION,
	CALC_RELATION,
	RELATION_COLUMN,
	RELATION_METHODS,
	STATEMENTS,
	STATEMENTS_ITEM,
	SPECIAL_OPERATORS,
	CURRENT_FORM,
	CURRENT_FORM_ITEM,
	BEANS,
	BEAN,
	BEAN_METHOD,
	RETURNTYPE,
	RETURNTYPE_CONSTANT,
	RETURNTYPE_ELEMENT,
	FORM_FOUNDSET,
	VALUELISTS,
	VALUELIST_ITEM,
	MEDIA,
	MEDIA_IMAGE,
	MEDIA_FOLDER,
	RESOURCES,
	COMPONENTS, //unique node in solex under Resources - holds folder and zip component packages
	COMPONENTS_PACKAGE, //node in solex under Resources/NG Components it is a folder or zip component package
	COMPONENTS_PROJECTS, //unique node in solex under each solution/module - holds project component packages
	COMPONENTS_PROJECT_PACKAGE, //node in solex under each Solution/NG Components it is a project component package
	COMPONENT, // one component - it can either belong to a folder/zip or to a project package
	XML_METHODS,
	XML_LIST_METHODS,
	JSUNIT,
	JSUNIT_ITEM,
	GRAYED_OUT,
	WORKING_SET,
	DATASOURCES,
	SERVICES, //unique node in solex under Resources - holds folder and zip service packages
	SERVICES_PACKAGE, //node in solex under Resources/NG Services it is a folder or zip service package
	SERVICES_PROJECTS, //node in solex under each solution/module - holds project component packages
	SERVICES_PROJECT_PACKAGE, //node in solex under each Solution/NG Services it is a project service package
	SERVICE, // one service - it can either belong to a folder/zip or to a project package
	COMPONENT_RESOURCE,
	SOLUTION_DATASOURCES,
	INMEMORY_DATASOURCES,
	INMEMORY_DATASOURCE
}